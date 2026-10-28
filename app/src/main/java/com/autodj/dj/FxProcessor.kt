package com.autodj.dj

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.autodj.dj.fx.FxChain
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import java.nio.ByteBuffer

/**
 * Runs a deck's [FxChain] inside ExoPlayer's audio pipeline. It sits before the time
 * stretcher, so it sees audio at the track's own rate and can count samples to know the
 * exact media time it is processing.
 */
@UnstableApi
class FxProcessor : BaseAudioProcessor() {
    @Volatile
    var plan: FxPlan = FxPlan.EMPTY

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
    }

    override fun onReset() {
        chain = null
        seekPending = true
    }

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
        const val SILENCE_ALERT_SEC = 3
        val QUIET_FX = setOf(FxType.MUTE, FxType.BRAKE, FxType.BACKSPIN)
    }
}
