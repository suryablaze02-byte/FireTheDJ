package com.autodj.analysis

import android.media.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmDecoderTest {
    private fun buf(size: Int, fill: ByteBuffer.() -> Unit) =
        ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()).apply(fill).also { it.flip() }

    private fun read(b: ByteBuffer, encoding: Int): FloatArray {
        val (n, out) = PcmDecoder.readSamples(b, encoding, FloatArray(0))
        return out.copyOf(n)
    }

    @Test
    fun sixteenBit() {
        val b = buf(6) { putShort(16384); putShort(-32768); putShort(0) }
        assertArrayEquals(floatArrayOf(0.5f, -1f, 0f), read(b, AudioFormat.ENCODING_PCM_16BIT), 1e-6f)
    }

    @Test
    fun hiResFlacAs24BitPacked() {
        // 0x400000 = half scale; 0xC00000 = minus half (sign from the top byte).
        val b = buf(6) { put(0); put(0); put(0x40); put(0); put(0); put(0xC0.toByte()) }
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), read(b, AudioFormat.ENCODING_PCM_24BIT_PACKED), 1e-6f)
    }

    @Test
    fun hiResFlacAs32BitAndFloat() {
        val i = buf(8) { putInt(Int.MIN_VALUE / 2); putInt(Int.MAX_VALUE / 4) }
        assertArrayEquals(floatArrayOf(-0.5f, 0.25f), read(i, AudioFormat.ENCODING_PCM_32BIT), 1e-6f)
        val f = buf(8) { putFloat(0.75f); putFloat(-0.125f) }
        assertArrayEquals(floatArrayOf(0.75f, -0.125f), read(f, AudioFormat.ENCODING_PCM_FLOAT), 0f)
    }

    @Test
    fun theScratchArrayIsReusedWhenBigEnough() {
        val scratch = FloatArray(16)
        val (n, out) = PcmDecoder.readSamples(buf(4) { putShort(1); putShort(2) }, AudioFormat.ENCODING_PCM_16BIT, scratch)
        assertEquals(2, n)
        assertEquals(scratch, out)
    }
}
