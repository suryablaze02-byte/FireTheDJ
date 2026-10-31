package com.autodj.dj.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class DemucsDspTest {
    /** PyTorch's own spectrogram conventions for htdemucs, written by the benchmark's fixture.py. */
    private class Fixture {
        val length: Int
        val frames: Int
        val x: FloatArray
        val spec: FloatArray
        val freq: FloatArray
        val wav: FloatArray

        init {
            val bytes = javaClass.getResourceAsStream("/stems/demucs_spec_fixture.bin")!!.readBytes()
            val f = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            fun take(n: Int) = FloatArray(n).also { f.get(it) }
            val header = take(2)
            length = header[0].toInt()
            frames = header[1].toInt()
            x = take(2 * length)
            spec = take(4 * DemucsDsp.BINS * frames)
            freq = take(4 * 4 * DemucsDsp.BINS * frames)
            wav = take(4 * 2 * length)
        }
    }

    private val fx = Fixture()

    private fun maxDiff(a: FloatArray, b: FloatArray): Float {
        var m = 0f
        for (i in a.indices) m = max(m, abs(a[i] - b[i]))
        return m
    }

    private fun peak(a: FloatArray) = a.maxOf { abs(it) }

    @Test
    fun spectrogramMatchesDemucs() {
        assertEquals(DemucsDsp.frames(fx.length), fx.frames)
        val left = fx.x.copyOfRange(0, fx.length)
        val right = fx.x.copyOfRange(fx.length, 2 * fx.length)
        val mine = DemucsDsp.spec(left, right, fx.length)
        val diff = maxDiff(mine, fx.spec)
        assertTrue("spec differs by $diff (peak ${peak(fx.spec)})", diff < 1e-4f * max(1f, peak(fx.spec)))
    }

    @Test
    fun inverseSpectrogramMatchesDemucs() {
        val perStem = 4 * DemucsDsp.BINS * fx.frames
        for (s in 0 until 4) {
            val l = FloatArray(fx.length)
            val r = FloatArray(fx.length)
            DemucsDsp.ispecAdd(fx.freq, s * perStem, fx.frames, fx.length, l, r)
            val refL = fx.wav.copyOfRange((s * 2) * fx.length, (s * 2 + 1) * fx.length)
            val refR = fx.wav.copyOfRange((s * 2 + 1) * fx.length, (s * 2 + 2) * fx.length)
            val d = max(maxDiff(l, refL), maxDiff(r, refR))
            assertTrue("stem $s waveform differs by $d (peak ${peak(refL)})", d < 1e-4f * max(1f, peak(refL)))
        }
    }

    @Test
    fun chunkingReassemblesWhatTheModelReturns() {
        // A stand-in network that gives back its input as "drums" (time branch only): the chunked,
        // weighted overlap-add must hand back exactly the original signal, whatever its length.
        val length = DemucsDsp.CHUNK * 2 + 12345
        val left = FloatArray(length) { sin(it * 0.01f) * 0.5f }
        val right = FloatArray(length) { sin(it * 0.013f) * 0.4f }
        var calls = 0
        val stems = DemucsDsp.separate(left, right, length, run = { mix, _ ->
            calls++
            val time = FloatArray(DemucsDsp.STEMS * 2 * DemucsDsp.CHUNK)
            System.arraycopy(mix, 0, time, 0, 2 * DemucsDsp.CHUNK)
            DemucsDsp.ChunkOutput(FloatArray(DemucsDsp.STEMS * 4 * DemucsDsp.BINS * DemucsDsp.CHUNK_FRAMES), time)
        })
        // Offsets every 0.75 chunk: 0, 128992, 257984.
        assertEquals(3, calls)
        assertTrue(maxDiff(stems[0][0], left) < 1e-5f)
        assertTrue(maxDiff(stems[0][1], right) < 1e-5f)
        assertTrue(stems[3][0].all { it == 0f })
    }

    @Test
    fun fftRoundTrips() {
        val n = 4096
        val fft = Fft(n)
        val re = DoubleArray(n) { sin(it * 0.3) + 0.2 * sin(it * 1.7) }
        val orig = re.copyOf()
        val im = DoubleArray(n)
        fft.forward(re, im)
        fft.inverse(re, im)
        for (i in 0 until n) assertEquals(orig[i], re[i], 1e-9)
    }
}
