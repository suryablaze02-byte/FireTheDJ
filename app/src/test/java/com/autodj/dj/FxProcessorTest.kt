package com.autodj.dj

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class FxProcessorTest {
    private val rate = 44100

    private fun feed(p: FxProcessor, frames: Int) {
        val buf = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames * 2) { buf.putShort(0) }
        buf.flip()
        p.queueInput(buf)
        p.getOutput()
    }

    @Test
    fun speedChangeFlushKeepsTheEffectsClockRunning() {
        val p = FxProcessor()
        p.seekTo(30.0)
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        feed(p, rate) // one second
        assertEquals(31.0, p.timeSec, 1e-6)

        // What ExoPlayer does on setPlaybackParameters: drain, then flush.
        p.queueEndOfStream()
        p.flush()
        feed(p, rate / 2)
        assertEquals(31.5, p.timeSec, 1e-6)

        // A real seek still starts over at the new position.
        p.seekTo(10.0)
        p.flush()
        assertEquals(10.0, p.timeSec, 1e-6)
    }
}
