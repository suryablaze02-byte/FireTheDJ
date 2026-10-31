package com.autodj.dj

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.autodj.dj.fx.FxChain
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import com.autodj.dj.stems.StemMix
import com.autodj.dj.stems.StemWindow
import com.autodj.dj.stems.StemWindows
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Runs a deck's [FxChain] inside ExoPlayer's audio pipeline. It sits before the time
 * stretcher, so it sees audio at the track's own rate and can count samples to know the
 * exact media time it is processing.
 *
 * Where a stretch of the song has been separated ([StemWindows]), it plays the four stems there
 * instead, mixed by the plan's bass and vocal moves ([StemMix]). Before that it lines the stems up
 * with the audio coming in, to the frame, so the switch can't be heard.
 */
@UnstableApi
class FxProcessor : BaseAudioProcessor() {
    @Volatile
    var plan: FxPlan = FxPlan.EMPTY

    /** The song the deck plays, to find its separated windows. */
    @Volatile
    var trackUri: String? = null

    /** Track position the next seek flush corresponds to (where the deck was loaded). */
    @Volatile
    private var startSec = 0.0

    @Volatile
    private var seekPending = true

    /** Live spectrum of this deck's output, for the visualizer. */
    val spectrum = SpectrumTap()

    private var chain: FxChain? = null
    private var channels = 2
    private var floats = FloatArray(0)
    private var dry = FloatArray(0)

    // The last couple of seconds of the incoming audio, mono, to line stem windows up against.
    private var ring = FloatArray(0)
    private var ringWrite = 0
    private var ringCount = 0
    private val gains = FloatArray(4)
    private val lastGains = FloatArray(4) { 1f }
    private var stemsWere: StemWindow? = null

    /** Media time of the next frame the chain will process. */
    val timeSec: Double get() = chain?.timeSec ?: startSec

    /** The deck is about to play from [sec]: the next flush starts the effects over there. */
    fun seekTo(sec: Double) {
        startSec = sec
        seekPending = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        chain = FxChain(inputAudioFormat.sampleRate, channels)
        spectrum.configure(inputAudioFormat.sampleRate)
        seekPending = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val chain = chain ?: return
        val samples = remaining / 2
        if (floats.size < samples) floats = FloatArray(samples)
        for (i in 0 until samples) floats[i] = inputBuffer.getShort() / 32768f
        chain.plan = plan
        val startSec = chain.timeSec
        var inPeak = 0f
        for (i in 0 until samples) inPeak = maxOf(inPeak, kotlin.math.abs(floats[i]))
        chain.stemmed = channels == 2 && stems(floats, samples / channels, startSec, chain.sampleRate)
        val heals = chain.heals
        if (dry.size < samples) dry = FloatArray(samples)
        System.arraycopy(floats, 0, dry, 0, samples)
        try {
            chain.process(floats, samples / channels)
        } catch (e: RuntimeException) {
            // A bug in an effect must never stop the music: play this buffer dry and start the effects over after it.
            Log.e("AutoDJ", "effects failed at ${"%.2f".format(startSec)} s; playing on without them", e)
            System.arraycopy(dry, 0, floats, 0, samples)
            chain.reset(startSec + samples / channels / chain.sampleRate.toDouble())
        }
        if (chain.heals != heals) Log.w("AutoDJ", "effects hit a broken number at ${"%.2f".format(startSec)} s; started them over")
        var outPeak = 0f
        for (i in 0 until samples) outPeak = maxOf(outPeak, kotlin.math.abs(floats[i]))
        watchSilence(inPeak, outPeak, samples / channels, startSec)
        spectrum.feed(floats, samples / channels, channels, startSec)
        val out = replaceOutputBuffer(remaining)
        for (i in 0 until samples) {
            out.putShort((floats[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
        }
        out.flip()
    }

    /**
     * ExoPlayer also flushes the pipeline on every speed change (it drains, then flushes),
     * and the audio carries on seamlessly after that. Only a real seek starts the effects
     * over; otherwise filters, echoes and the media clock keep running, or every beat-lock
     * nudge would be heard as a tick.
     */
    override fun onFlush() {
        if (!seekPending) return
        seekPending = false
        chain?.let {
            it.plan = plan
            it.reset(startSec)
        }
        spectrum.reset()
        ringCount = 0
        stemsWere = null
    }

    override fun onReset() {
        chain = null
        seekPending = true
    }

    /**
     * Plays this buffer from the song's stems if a lined-up window covers it, and starts lining
     * one up when the deck is a little way into it. True when the buffer now holds stems.
     */
    private fun stems(buf: FloatArray, frames: Int, sec: Double, rate: Int): Boolean {
        remember(buf, frames, rate)
        val w = StemWindows.at(trackUri, sec)
        if (w == null || w.sampleRate != rate) {
            stemsWere = null
            return false
        }
        val into = sec - w.startSec
        val endSec = sec + frames / rate.toDouble()
        when (w.state) {
            // Ready only after the deck was well into it: switching sound halfway through a move would be heard.
            StemWindow.State.WAITING -> if (into > LATE_SEC) {
                w.state = StemWindow.State.LATE
                Log.i("AutoDJ", "stems: \"${w.title}\" were ready too late for this mix; it uses EQ")
            } else if (into >= ALIGN_AFTER_SEC && ringCount >= (ALIGN_SEC * rate).toInt()) {
                w.state = StemWindow.State.ALIGNING
                val live = recent((ALIGN_SEC * rate).toInt())
                val liveIndex = ((endSec - ALIGN_SEC - w.startSec) * rate).roundToInt()
                aligner.execute {
                    val lag = runCatching { StemMix.align(live, w.guide, liveIndex, (MAX_LAG_SEC * rate).toInt()) }.getOrNull()
                    if (lag != null) {
                        w.lag = lag
                        w.state = StemWindow.State.READY
                    } else {
                        w.state = StemWindow.State.FAILED
                        Log.w("AutoDJ", "stems: \"${w.title}\" didn't line up with the deck; this mix uses EQ")
                    }
                }
            }
            StemWindow.State.READY -> if (into >= USE_AFTER_SEC && endSec <= w.endSec - USE_MARGIN_SEC) {
                val base = (into * rate).roundToInt() + w.lag
                if (base < 0 || base + frames > w.frames) return false
                if (stemsWere !== w) {
                    stemsWere = w
                    lastGains.fill(1f)
                    Log.i("AutoDJ", "stems: playing \"${w.title}\" from its stems at ${"%.1f".format(sec)} s (lined up ${"%+.1f".format(w.lag * 1000.0 / rate)} ms)")
                }
                mix(w, buf, frames, base, sec, rate)
                return true
            }
            else -> Unit
        }
        stemsWere = null
        return false
    }

    /** The buffer rebuilt from the window's stems, each at its gain, gains eased block to block. */
    private fun mix(w: StemWindow, buf: FloatArray, frames: Int, base: Int, sec: Double, rate: Int) {
        val p = plan
        val (drums, bass, other, vocals) = w.stems
        var f = 0
        while (f < frames) {
            val n = minOf(GAIN_BLOCK, frames - f)
            StemMix.gains(p, sec + (f + n / 2.0) / rate, gains)
            for (i in 0 until n) {
                val x = (i + 1).toFloat() / n
                val gd = lastGains[0] + (gains[0] - lastGains[0]) * x
                val gb = lastGains[1] + (gains[1] - lastGains[1]) * x
                val go = lastGains[2] + (gains[2] - lastGains[2]) * x
                val gv = lastGains[3] + (gains[3] - lastGains[3]) * x
                val k = 2 * (base + f + i)
                for (c in 0..1) {
                    buf[2 * (f + i) + c] = (drums.get(k + c) * gd + bass.get(k + c) * gb + other.get(k + c) * go + vocals.get(k + c) * gv) / 32768f
                }
            }
            System.arraycopy(gains, 0, lastGains, 0, 4)
            f += n
        }
    }

    private fun remember(buf: FloatArray, frames: Int, rate: Int) {
        val size = (RING_SEC * rate).toInt()
        if (ring.size != size) { ring = FloatArray(size); ringWrite = 0; ringCount = 0 }
        for (i in 0 until frames) {
            ring[ringWrite] = if (channels == 2) (buf[2 * i] + buf[2 * i + 1]) * 0.5f else buf[i * channels]
            ringWrite = (ringWrite + 1) % size
        }
        ringCount = minOf(ringCount + frames, size)
    }

    /** The last [n] frames remembered, oldest first. */
    private fun recent(n: Int) = FloatArray(n) { ring[Math.floorMod(ringWrite - n + it, ring.size)] }

    private var silentFrames = 0L

    /**
     * Music going in and nothing coming out, with the deck turned up and no gap planned, is
     * never on purpose: after a few seconds it's logged with what was on, and the effects
     * start over so the music comes back.
     */
    private fun watchSilence(inPeak: Float, outPeak: Float, frames: Int, sec: Double) {
        val chain = chain ?: return
        val p = plan
        val meant = p.gain.at(sec) < 0.3f || p.events.any { it.type in QUIET_FX && it.start <= sec && it.end + 1.0 > sec }
        silentFrames = if (inPeak > 0.02f && outPeak < 0.001f && !meant) silentFrames + frames else 0
        if (silentFrames < SILENCE_ALERT_SEC * chain.sampleRate) return
        silentFrames = 0
        val on = p.events.filter { it.start <= sec && it.end > sec }.joinToString { it.type.name }.ifEmpty { "none" }
        Log.w("AutoDJ", "deck silent at ${"%.2f".format(sec)} s with music going in (gain ${p.gain.at(sec)}, effects: $on); starting effects over")
        chain.heal()
    }

    private companion object {
        /** Lining up: once this far into a window, on the last 0.6 s of audio, within ±0.3 s. */
        const val ALIGN_AFTER_SEC = 1.0
        const val ALIGN_SEC = 0.6
        const val MAX_LAG_SEC = 0.3
        const val RING_SEC = 2.0

        /** Stems play from here into a window to just short of its end, where the plain audio takes back over. */
        const val USE_AFTER_SEC = 1.0

        /** A window first reached this far in is left alone (see [stems]). */
        const val LATE_SEC = 3.0
        const val USE_MARGIN_SEC = 0.3
        const val GAIN_BLOCK = 64

        /** Lining up is a few million multiplies: off the audio thread. */
        val aligner = Executors.newSingleThreadExecutor { r -> Thread(r, "stems-align").apply { isDaemon = true } }

        const val SILENCE_ALERT_SEC = 3
        val QUIET_FX = setOf(FxType.MUTE, FxType.BRAKE, FxType.BACKSPIN)
    }
}
