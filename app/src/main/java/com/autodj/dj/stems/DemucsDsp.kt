package com.autodj.dj.stems

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The signal processing around Demucs `htdemucs` (Hybrid Transformer Demucs, MIT), which the
 * exported model leaves out because ONNX has no complex numbers: the spectrogram it reads, the
 * inverse spectrogram of what it writes, and the chunking of a long window into the model's
 * fixed-length pieces. Each step follows `demucs.htdemucs` and `demucs.apply` exactly, so the
 * stems match Demucs's own (checked against PyTorch output in the unit tests).
 */
object DemucsDsp {
    const val SAMPLE_RATE = 44100
    const val NFFT = 4096
    const val HOP = NFFT / 4
    /** Frequency bins the model sees: the STFT's, less the Nyquist bin. */
    const val BINS = NFFT / 2
    const val STEMS = 4

    /** The stems the model writes, in its order. */
    val NAMES = listOf("drums", "bass", "other", "vocals")

    /** Samples per chunk: 3.9 s, half the length the model was trained on, which halves its memory. */
    const val CHUNK = 171990

    /** Frames in a chunk's spectrogram. */
    val CHUNK_FRAMES = frames(CHUNK)

    fun frames(length: Int) = ceil(length / HOP.toDouble()).toInt()

    private val window = DoubleArray(NFFT) { 0.5 - 0.5 * cos(2 * PI * it / NFFT) }
    private val fft = Fft(NFFT)
    /** stft(normalized=True) divides by sqrt(NFFT); istft(normalized=True) multiplies back. */
    private val scale = 1.0 / sqrt(NFFT.toDouble())
    private val unscale = sqrt(NFFT.toDouble())

    /**
     * `HTDemucs._magnitude(HTDemucs._spec(x))`: the complex spectrogram of each channel, real
     * and imaginary parts as channels (L re, L im, R re, R im), [4][BINS][frames] flattened.
     */
    fun spec(left: FloatArray, right: FloatArray, length: Int, out: FloatArray = FloatArray(4 * BINS * frames(length))): FloatArray {
        val le = frames(length)
        val re = DoubleArray(NFFT)
        val im = DoubleArray(NFFT)
        for ((c, x) in listOf(left, right).withIndex()) {
            val padded = padForSpec(x, length)
            // torch.stft(center=True) reflect-pads NFFT/2 more; frames 2 .. 2+le are kept.
            val centred = reflectPad(padded, padded.size, NFFT / 2, NFFT / 2)
            for (k in 0 until le) {
                val start = (k + 2) * HOP
                for (n in 0 until NFFT) { re[n] = centred[start + n] * window[n]; im[n] = 0.0 }
                fft.forward(re, im)
                val baseRe = ((c * 2) * BINS) * le + k
                val baseIm = ((c * 2 + 1) * BINS) * le + k
                for (f in 0 until BINS) {
                    out[baseRe + f * le] = (re[f] * scale).toFloat()
                    out[baseIm + f * le] = (im[f] * scale).toFloat()
                }
            }
        }
        return out
    }

    /** `pad1d(x, (pad, pad + le*hop - L), mode="reflect")` from `HTDemucs._spec`. */
    private fun padForSpec(x: FloatArray, length: Int): DoubleArray {
        val pad = HOP / 2 * 3
        val le = frames(length)
        return reflectPad(DoubleArray(length) { x[it].toDouble() }, length, pad, pad + le * HOP - length)
    }

    /** torch's reflect padding: the edge sample isn't repeated. Inputs here are always longer than the padding. */
    private fun reflectPad(x: DoubleArray, length: Int, left: Int, right: Int): DoubleArray {
        require(length > maxOf(left, right)) { "too short to reflect-pad: $length" }
        return DoubleArray(length + left + right) { i ->
            var j = i - left
            if (j < 0) j = -j
            if (j >= length) j = 2 * (length - 1) - j
            x[j]
        }
    }

    /**
     * `HTDemucs._ispec(HTDemucs._mask(z, m), length)` for one stem: [spec] holds that stem's
     * (L re, L im, R re, R im) × [BINS] × [frames] as the model wrote it; the waveform's two
     * channels are added into [left] and [right] (length [length]).
     */
    fun ispecAdd(spec: FloatArray, offset: Int, frames: Int, length: Int, left: FloatArray, right: FloatArray) {
        val pad = HOP / 2 * 3
        val le = HOP * ceil(length / HOP.toDouble()).toInt() + 2 * pad
        // z padded with a zero Nyquist bin and two silent frames each side, then istft(center=True).
        val allFrames = frames + 4
        val olaLen = NFFT + HOP * (allFrames - 1)
        val re = DoubleArray(NFFT)
        val im = DoubleArray(NFFT)
        for ((c, out) in listOf(left, right).withIndex()) {
            val ola = DoubleArray(olaLen)
            val env = DoubleArray(olaLen)
            for (k in 0 until allFrames) {
                val src = k - 2
                java.util.Arrays.fill(re, 0.0)
                java.util.Arrays.fill(im, 0.0)
                if (src in 0 until frames) {
                    val baseRe = offset + ((c * 2) * BINS) * frames + src
                    val baseIm = offset + ((c * 2 + 1) * BINS) * frames + src
                    for (f in 0 until BINS) {
                        re[f] = spec[baseRe + f * frames].toDouble()
                        im[f] = spec[baseIm + f * frames].toDouble()
                    }
                    // A real signal's spectrum is mirrored; the DC bin's imaginary part is ignored, as irfft does.
                    im[0] = 0.0
                    for (f in 1 until BINS) { re[NFFT - f] = re[f]; im[NFFT - f] = -im[f] }
                    fft.inverse(re, im)
                }
                val start = k * HOP
                for (n in 0 until NFFT) {
                    // The inverse FFT's 1/N is already in; istft(normalized=True) multiplies by sqrt(N).
                    ola[start + n] += re[n] * unscale * window[n]
                    env[start + n] += window[n] * window[n]
                }
            }
            // center=True drops NFFT/2 at the start; then Demucs keeps [pad, pad + length).
            for (i in 0 until length) {
                val j = NFFT / 2 + pad + i
                if (i + pad < le && j < olaLen && env[j] > 1e-11) out[i] += (ola[j] / env[j]).toFloat()
            }
        }
    }

    /** What the exported network computes for one chunk: the frequency branch's spectrogram and the time branch's waveform. */
    class ChunkOutput(
        /** [STEMS][4][BINS][CHUNK_FRAMES]. */
        val freq: FloatArray,
        /** [STEMS][2][CHUNK]. */
        val time: FloatArray,
    )

    /**
     * Separates [left]/[right] (44.1 kHz, [length] frames) into four stems, as `demucs.apply.apply_model`
     * does with split=True, overlap 0.25: overlapping chunks of [CHUNK], each padded with its
     * neighbours' audio when it's short, blended with a triangle weight. [run] is the network.
     * Returns [STEMS][2 channels] arrays of [length].
     */
    fun separate(
        left: FloatArray,
        right: FloatArray,
        length: Int,
        run: (mix: FloatArray, spec: FloatArray) -> ChunkOutput,
        onChunk: (done: Int, of: Int) -> Unit = { _, _ -> },
    ): Array<Array<FloatArray>> {
        val stride = (0.75 * CHUNK).toInt()
        val offsets = (0 until length step stride).toList()
        val out = Array(STEMS) { Array(2) { FloatArray(length) } }
        val sumWeight = FloatArray(length)
        val half = CHUNK / 2
        val weight = FloatArray(CHUNK) { i -> (if (i < half) i + 1 else CHUNK - i).toFloat() / half }
        val mix = FloatArray(2 * CHUNK)
        val l = FloatArray(CHUNK)
        val r = FloatArray(CHUNK)
        val spec = FloatArray(4 * BINS * CHUNK_FRAMES)
        val wl = FloatArray(CHUNK)
        val wr = FloatArray(CHUNK)
        for ((n, offset) in offsets.withIndex()) {
            val chunkLen = min(CHUNK, length - offset)
            // TensorChunk.padded: centred on the chunk, filled with the real audio around it.
            val delta = CHUNK - chunkLen
            val from = offset - delta / 2
            for (i in 0 until CHUNK) {
                val j = from + i
                val inside = j in 0 until length
                l[i] = if (inside) left[j] else 0f
                r[i] = if (inside) right[j] else 0f
            }
            System.arraycopy(l, 0, mix, 0, CHUNK)
            System.arraycopy(r, 0, mix, CHUNK, CHUNK)
            spec(l, r, CHUNK, spec)
            val res = run(mix, spec)
            val perStem = 4 * BINS * CHUNK_FRAMES
            for (s in 0 until STEMS) {
                java.util.Arrays.fill(wl, 0f)
                java.util.Arrays.fill(wr, 0f)
                ispecAdd(res.freq, s * perStem, CHUNK_FRAMES, CHUNK, wl, wr)
                val tBase = s * 2 * CHUNK
                // center_trim back to the chunk, then the weighted overlap-add.
                val trim = delta / 2
                for (i in 0 until chunkLen) {
                    val src = trim + i
                    val w = weight[i]
                    out[s][0][offset + i] += w * (wl[src] + res.time[tBase + src])
                    out[s][1][offset + i] += w * (wr[src] + res.time[tBase + CHUNK + src])
                }
            }
            for (i in 0 until chunkLen) sumWeight[offset + i] += weight[i]
            onChunk(n + 1, offsets.size)
        }
        for (s in 0 until STEMS) for (c in 0..1) {
            val a = out[s][c]
            for (i in 0 until length) if (sumWeight[i] > 0f) a[i] /= sumWeight[i]
        }
        return out
    }
}

/** An in-place radix-2 complex FFT of a fixed power-of-two size. */
class Fft(private val n: Int) {
    private val levels = Integer.numberOfTrailingZeros(n)
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val rev = IntArray(n) { Integer.reverse(it) ushr (32 - levels) }

    init {
        require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" }
    }

    /** e^(−2πi·k·n/N): the forward transform, unscaled. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, -1.0)

    /** e^(+2πi·k·n/N), scaled by 1/N: the inverse. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, 1.0)
        val s = 1.0 / n
        for (i in 0 until n) { re[i] *= s; im[i] *= s }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, sign: Double) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + halfSize) {
                    val wr = cosT[k]
                    val wi = sign * sinT[k]
                    val l = j + halfSize
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }
}
