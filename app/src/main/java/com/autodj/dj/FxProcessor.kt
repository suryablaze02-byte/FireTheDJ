package com.autodj.dj

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.autodj.dj.fx.FxChain
import com.autodj.dj.fx.FxPlan
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
        chain.process(floats, samples / channels)
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
}
