package com.autodj.dj.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * One deck's effects, applied to interleaved float audio in place. Every parameter comes
 * from [plan], evaluated at the media time of the samples being processed, so beat-synced
 * effects (gates, rolls, echoes) stay on the grid however far ahead the player buffers.
 *
 * Signal flow per frame:
 * source (normal | loop roll | scratch | backspin) → bass shelf → high-pass → low-pass →
 * gate/tremolo → lo-fi → flanger → phaser → × gain, plus echo and reverb sends that keep
 * ringing after the dry signal is cut (that is what makes an echo-out).
 */
class FxChain(sampleRate: Int, private val channels: Int) {
    @Volatile
    var plan: FxPlan = FxPlan.EMPTY

    private val sr = sampleRate.toDouble()
    private var baseSec = 0.0
    private var frames = 0L

    /** Media time of the next frame to be processed. */
    val timeSec: Double get() = baseSec + frames / sr

    // EQ and filters (RBJ biquads).
    private val shelf = Biquad(channels)
    private val hp = Biquad(channels)
    private val lp = Biquad(channels)
    private var lastBassDb = Float.NaN
    private var lastHp = Float.NaN
    private var lastLp = Float.NaN

    // Recent input, for the backspin to play backwards through.
    private val histFrames = (4 * sr).toInt()
    private val history = FloatArray(histFrames * channels)
    private var histWrite = 0

    // Loop roll / scratch capture.
    // A full bar even at 60 BPM, for Loop & ramp.
    private val capFrames = (4.5 * sr).toInt()
    private val capture = FloatArray(capFrames * channels)
    private var capMode: FxType? = null
    private var capPos = 0
    private var captured = 0

    private var spinOn = false
    private var spinRead = 0.0
    private var brakeOn = false
    private var brakeRead = 0.0

    private val sfx = SfxSynth(sampleRate)
    private val sfxVerb = Reverb(sampleRate, channels).apply { setRt60(SFX_RT60) }
    private var sfxTailUntil = Double.NEGATIVE_INFINITY

    private var gateGain = 1f
    private val gateCoef = (1 - exp(-1.0 / (0.0015 * sr))).toFloat()

    private val hold = FloatArray(channels)
    private var holdCount = 0

    private val flFrames = 2048
    private val flBuf = FloatArray(flFrames * channels)
    private var flWrite = 0

    private val phX = FloatArray(4 * channels)
    private val phY = FloatArray(4 * channels)
    private val phLast = FloatArray(channels)

    private val echoFrames = (3 * sr).toInt()
    private val echoBuf = FloatArray(echoFrames * channels)
    private var echoWrite = 0
    private val echoLp = FloatArray(channels)
    private var echoUntil = Double.NEGATIVE_INFINITY
    private var echoDelay = 1
    private var echoFb = 0.5f
    private var echoDirty = false

    private val reverb = Reverb(sampleRate, channels)
    private var reverbUntil = Double.NEGATIVE_INFINITY

    // Mid/side vocal tools (stereo only): vocals sit in the centre.
    private val vocalLow = Biquad(1).apply { lowPass(180.0, 0.7, sr) }
    private val vocalHp = Biquad(1).apply { highPass(250.0, 0.7, sr) }
    private val vocalLp = Biquad(1).apply { lowPass(5000.0, 0.7, sr) }

    private var gainNow = 1f
    private val src = FloatArray(channels)

    /** Starts over at [startSec] (after a seek or a new track), dropping all effect state. */
    fun reset(startSec: Double) {
        baseSec = startSec
        frames = 0
        listOf(shelf, hp, lp).forEach { it.reset() }
        lastBassDb = Float.NaN; lastHp = Float.NaN; lastLp = Float.NaN
        history.fill(0f); histWrite = 0
        capMode = null; capPos = 0; captured = 0
        spinOn = false
        brakeOn = false
        sfx.reset()
        sfxVerb.clear(); sfxTailUntil = Double.NEGATIVE_INFINITY
        gateGain = 1f
        listOf(vocalLow, vocalHp, vocalLp).forEach { it.reset() }
        flBuf.fill(0f); flWrite = 0
        phX.fill(0f); phY.fill(0f); phLast.fill(0f)
        echoBuf.fill(0f); echoLp.fill(0f); echoUntil = Double.NEGATIVE_INFINITY; echoDirty = false
        reverb.clear(); reverbUntil = Double.NEGATIVE_INFINITY
        gainNow = plan.gain.at(startSec)
    }

    fun process(buf: FloatArray, frameCount: Int) {
        val p = plan
        var i = 0
        while (i < frameCount) {
            val n = min(BLOCK, frameCount - i)
            processBlock(p, buf, i, n)
            i += n
        }
    }

    private fun processBlock(p: FxPlan, buf: FloatArray, offset: Int, n: Int) {
        val t0 = timeSec
        val tMid = t0 + n / 2.0 / sr
        val beat = p.beatSec

        val bassDb = (20 * log10(max(p.bass.at(tMid), 0.02f).toDouble())).toFloat()
        if (bassDb != lastBassDb) { shelf.lowShelf(120.0, bassDb.toDouble(), sr); lastBassDb = bassDb }
        val hpAmt = p.active(FxType.HIGH_PASS, tMid)?.amount(tMid) ?: 0f
        if (hpAmt != lastHp) { hp.highPass(FxPlan.highPassHz(hpAmt), 0.9, sr); lastHp = hpAmt }
        val lpAmt = p.active(FxType.LOW_PASS, tMid)?.amount(tMid) ?: 0f
        if (lpAmt != lastLp) { lp.lowPass(min(FxPlan.lowPassHz(lpAmt), 0.45 * sr), 0.9, sr); lastLp = lpAmt }

        val gainStart = gainNow
        val gainEnd = p.gain.at(t0 + n / sr)

        val gate = p.active(FxType.GATE, tMid)
        val tremolo = p.active(FxType.TREMOLO, tMid)
        val lofi = p.active(FxType.LOFI, tMid)
        val flanger = p.active(FxType.FLANGER, tMid)
        val phaser = p.active(FxType.PHASER, tMid)
        val vocalCut = if (channels == 2) p.active(FxType.VOCAL_CUT, tMid) else null
        val vocalIso = if (channels == 2) p.active(FxType.VOCAL_ISOLATE, tMid) else null

        // Loop roll and scratch share the capture buffer; a new one starts when the mode changes.
        val capEvent = p.active(FxType.LOOP_ROLL, tMid) ?: p.active(FxType.SCRATCH, tMid)
        if (capEvent == null) {
            capMode = null
        } else if (capMode != capEvent.type) {
            capMode = capEvent.type
            capPos = 0
            captured = 0
        }
        val rollLen = if (capEvent?.type == FxType.LOOP_ROLL) (capEvent.p1 * beat * sr).toInt().coerceIn(64, capFrames) else 0
        val segLen = if (capEvent?.type == FxType.SCRATCH) (capEvent.p1 * beat * sr).toInt().coerceIn(64, capFrames) else 0
        val stroke = if (capEvent?.type == FxType.SCRATCH) max(64.0, capEvent.p2 * beat * sr) else 1.0

        val spin = p.active(FxType.BACKSPIN, tMid)
        if (spin == null) {
            spinOn = false
        } else if (!spinOn) {
            spinOn = true
            spinRead = (histWrite - 1).toDouble()
        }
        val brake = p.active(FxType.BRAKE, tMid)
        if (brake == null) {
            brakeOn = false
        } else if (!brakeOn) {
            brakeOn = true
            brakeRead = (histWrite - 1).toDouble()
        }
        val sfxEvents = if (p.hasSfx) p.events.filter { it.type.isSfx && it.isActive(tMid) } else emptyList()
        sfxEvents.maxOfOrNull { it.end }?.let { sfxTailUntil = maxOf(sfxTailUntil, it + SFX_RT60) }
        val sfxOn = t0 < sfxTailUntil
        // Where the source jumps (a loop, scratch, brake or spin starts, ends or changes length),
        // the output dips smoothly to zero and back over a few ms instead of clicking.
        val blockEnd = t0 + n / sr
        val edges = p.events.filter { it.type in SOURCE_TYPES }.flatMap { listOf(it.start, it.end) }
            .filter { it > t0 - EDGE_SEC && it < blockEnd + EDGE_SEC }
        sfx.prepare(sfxEvents, tMid)

        val echoEv = p.active(FxType.ECHO, tMid)
        if (echoEv != null) {
            echoDelay = (echoEv.p1 * beat * sr).toInt().coerceIn(1, echoFrames - 1)
            echoFb = echoEv.p2.coerceIn(0f, 0.95f)
            val tail = echoDelay / sr * ln(0.001) / ln(max(echoFb, 0.05f).toDouble())
            echoUntil = echoEv.end + tail
        }
        val echoSend = echoEv?.amount(tMid) ?: 0f
        val echoOn = t0 < echoUntil
        if (!echoOn && echoDirty) { echoBuf.fill(0f); echoLp.fill(0f); echoDirty = false }

        val revEv = p.active(FxType.REVERB, tMid)
        if (revEv != null) {
            reverb.setRt60(revEv.p1)
            reverbUntil = revEv.end + revEv.p1
        }
        val revSend = revEv?.amount(tMid) ?: 0f
        val revOn = t0 < reverbUntil

        val lofiAmt = lofi?.amount(tMid) ?: 0f
        val lofiDrive = 1f + 3f * lofiAmt
        val lofiNorm = tanh(lofiDrive)
        val lofiLevels = if (lofi != null) 2.0.pow((16 - lofiAmt * (16 - lofi.p1)).toDouble() - 1).toFloat() else 0f
        val lofiFactor = if (lofi != null) 1 + (lofiAmt * (lofi.p2 - 1)).roundToInt() else 1

        for (k in 0 until n) {
            val idx = (offset + k) * channels
            val t = t0 + k / sr
            for (c in 0 until channels) history[histWrite * channels + c] = buf[idx + c]
            histWrite = (histWrite + 1) % histFrames

            // Source: what the "platter" is playing right now.
            when {
                spin != null -> {
                    val x = ((t - spin.start) / (spin.end - spin.start)).coerceIn(0.0, 1.0)
                    spinRead -= spin.p1 * (1 - x) * (1 - x)
                    if (spinRead < 0) spinRead += histFrames
                    val fade = sqrt(1 - x).toFloat()
                    for (c in 0 until channels) src[c] = readRing(history, histFrames, spinRead, c) * fade
                }
                brake != null -> {
                    // Forward through recent audio at a falling rate: the platter winding down.
                    val x = ((t - brake.start) / (brake.end - brake.start)).coerceIn(0.0, 1.0)
                    brakeRead += (1 - x).pow(1.5)
                    if (brakeRead >= histFrames) brakeRead -= histFrames
                    val fade = sqrt(1 - x).toFloat()
                    for (c in 0 until channels) src[c] = readRing(history, histFrames, brakeRead, c) * fade
                }
                capMode == FxType.LOOP_ROLL -> {
                    if (capPos == captured && captured < rollLen) {
                        // The first pass fades into the wrap point just like every repeat does.
                        val w = min(1f, (rollLen - captured) / 48f)
                        for (c in 0 until channels) {
                            capture[captured * channels + c] = buf[idx + c]
                            src[c] = buf[idx + c] * w
                        }
                        captured++
                    } else {
                        val len = min(rollLen, captured)
                        val j = capPos % len
                        val w = min(1f, min(j / 48f, (len - j) / 48f))
                        for (c in 0 until channels) src[c] = capture[j * channels + c] * w
                    }
                    capPos++
                }
                capMode == FxType.SCRATCH -> {
                    if (capPos == captured && captured < segLen) {
                        for (c in 0 until channels) {
                            capture[captured * channels + c] = buf[idx + c]
                            src[c] = buf[idx + c]
                        }
                        captured++
                    } else {
                        // Back then forward over the captured segment, like a hand on the record. It
                        // starts where the audio is (the segment's end) at zero speed, and eases in
                        // and out over the live track, which keeps playing softly underneath.
                        val u = ((capPos - captured) % stroke) / stroke
                        val pos = (captured - 1) * (0.5 + 0.5 * cos(2 * PI * u))
                        val scratchFrom = capEvent!!.start + captured / sr
                        val ramp = min((t - scratchFrom) / SCRATCH_FADE, (capEvent.end - t) / SCRATCH_FADE).coerceIn(0.0, 1.0)
                        val env = sin(ramp * PI / 2).toFloat()
                        val bed = 1f - env * (1f - SCRATCH_BED)
                        for (c in 0 until channels) src[c] = buf[idx + c] * bed + readLinear(capture, captured, pos, c) * env
                    }
                    capPos++
                }
                else -> for (c in 0 until channels) src[c] = buf[idx + c]
            }

            if (vocalCut != null || vocalIso != null) {
                var m = (src[0] + src[1]) * 0.5f
                var s = (src[0] - src[1]) * 0.5f
                if (vocalCut != null) {
                    // Keep the centre's bass and kick, remove the rest of the centre: the vocal.
                    val low = vocalLow.process(m, 0)
                    m = low + (m - low) * (1 - 0.92f * vocalCut.amount(t))
                }
                if (vocalIso != null) {
                    // Centre only, vocal band only: an approximate acapella.
                    val a = vocalIso.amount(t)
                    val band = vocalLp.process(vocalHp.process(m, 0), 0)
                    m = m * (1 - a) + band * a
                    s *= 1 - a
                }
                src[0] = m + s
                src[1] = m - s
            }

            if (edges.isNotEmpty()) {
                var dip = 1.0
                for (edge in edges) dip = min(dip, abs(t - edge) / EDGE_SEC)
                if (dip < 1.0) {
                    val g = sin(dip * PI / 2).toFloat()
                    for (c in 0 until channels) src[c] *= g
                }
            }

            // Rhythmic gain: square gate or sine tremolo, phase from the playhead modulo the period.
            val rhythm = gate ?: tremolo
            val target = if (rhythm == null) {
                1f
            } else {
                val period = rhythm.p1 * beat
                val ph = frac((t - p.downbeatSec) / period)
                val depth = rhythm.amount(t)
                if (rhythm === gate) (if (ph < 0.5) 1f else 1f - depth)
                else 1f - depth * (0.5f - 0.5f * cos(2 * PI * ph).toFloat())
            }
            gateGain += (target - gateGain) * gateCoef

            val flDelay = if (flanger != null) (1.0 + 2.5 * (1 + sin(2 * PI * flanger.p1 * t))) * sr / 1000 else 0.0
            val flMix = flanger?.amount(t) ?: 0f
            val phA = if (phaser != null) {
                val fc = 300.0 * (1600.0 / 300.0).pow((1 + sin(2 * PI * phaser.p1 * t)) / 2)
                val tn = tan(PI * fc / sr)
                ((tn - 1) / (tn + 1)).toFloat()
            } else 0f
            val phMix = phaser?.amount(t) ?: 0f
            val gain = gainStart + (gainEnd - gainStart) * (k + 1) / n
            // Effects bus: ducked under each kick (sidechain) so they pump with the track instead of
            // sitting on top of it, and sent to a short reverb so they share the room with the music.
            val effect = if (sfxEvents.isEmpty()) 0f else {
                val sinceKick = frac((t - p.downbeatSec) / beat) * beat
                sfx.sample(sfxEvents, t, beat) * (1f - SFX_DUCK * exp(-sinceKick / 0.09).toFloat())
            }

            for (c in 0 until channels) {
                var x = src[c]
                x = shelf.process(x, c)
                x = hp.process(x, c)
                x = lp.process(x, c)
                x *= gateGain

                if (lofi != null) {
                    x = tanh(lofiDrive * x) / lofiNorm
                    x = (x * lofiLevels).roundToInt() / lofiLevels
                    if (holdCount == 0) hold[c] = x
                    x = hold[c]
                }
                if (flanger != null) {
                    val d = readRing(flBuf, flFrames, flWrite - flDelay, c)
                    flBuf[flWrite * channels + c] = x + 0.5f * d
                    x = x * (1 - flMix * 0.5f) + d * flMix * 0.5f
                }
                if (phaser != null) {
                    var v = x + 0.5f * phLast[c]
                    for (s in 0 until 4) {
                        val i = c * 4 + s
                        val y = phA * v + phX[i] - phA * phY[i]
                        phX[i] = v
                        phY[i] = y
                        v = y
                    }
                    phLast[c] = v
                    x = x * (1 - phMix * 0.5f) + v * phMix * 0.5f
                }

                var out = x * gain
                if (echoOn) {
                    var read = echoWrite - echoDelay
                    if (read < 0) read += echoFrames
                    val y = echoBuf[read * channels + c]
                    echoLp[c] += 0.5f * (y - echoLp[c])
                    echoBuf[echoWrite * channels + c] = x * echoSend + echoFb * echoLp[c]
                    out += y * ECHO_WET
                    echoDirty = true
                }
                if (revOn) out += reverb.process(x * revSend, c)
                if (sfxOn) out += effect * SFX_DRY + sfxVerb.process(effect * SFX_SEND, c)
                buf[idx + c] = softClip(out)
            }
            if (lofi != null) holdCount = (holdCount + 1) % lofiFactor
            if (flanger != null) flWrite = (flWrite + 1) % flFrames
            if (echoOn) echoWrite = (echoWrite + 1) % echoFrames
            frames++
        }
        gainNow = gainEnd
    }

    private fun readRing(ring: FloatArray, size: Int, pos: Double, c: Int): Float {
        var p = pos % size
        if (p < 0) p += size
        val i0 = floor(p).toInt()
        val i1 = (i0 + 1) % size
        val f = (p - i0).toFloat()
        return ring[i0 * channels + c] * (1 - f) + ring[i1 * channels + c] * f
    }

    private fun readLinear(buf: FloatArray, size: Int, pos: Double, c: Int): Float {
        val p = pos.coerceIn(0.0, (size - 1).toDouble())
        val i0 = floor(p).toInt()
        val i1 = min(i0 + 1, size - 1)
        val f = (p - i0).toFloat()
        return buf[i0 * channels + c] * (1 - f) + buf[i1 * channels + c] * f
    }

    private fun frac(x: Double) = x - floor(x)

    /** Transparent below 0.8; above, eases peaks (loud effects on a loud track) under 1.0. */
    private fun softClip(x: Float): Float {
        val ax = abs(x)
        if (ax <= 0.8f) return x
        val over = (ax - 0.8f) / 0.2f
        return kotlin.math.sign(x) * (0.8f + 0.2f * tanh(over))
    }

    private companion object {
        const val BLOCK = 32
        const val ECHO_WET = 0.85f

        // Sound-effect bus: dry level, reverb send and room size, and how far each kick ducks it.
        const val SFX_DRY = 0.85f
        const val SFX_SEND = 0.35f
        const val SFX_RT60 = 1.4f
        const val SFX_DUCK = 0.45f

        /** Crossfade into and out of a scratch, over the live beat. */
        const val SCRATCH_FADE = 0.03

        /** How much of the live track keeps playing under a scratch, so the groove never stops. */
        const val SCRATCH_BED = 0.5f

        /** Half-width of the click-free dip at a source jump. */
        const val EDGE_SEC = 0.008
        val SOURCE_TYPES = setOf(FxType.LOOP_ROLL, FxType.BACKSPIN, FxType.BRAKE)
    }
}

/** RBJ-cookbook biquad, transposed direct form II, one state pair per channel. */
class Biquad(channels: Int) {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private val z1 = FloatArray(channels)
    private val z2 = FloatArray(channels)

    fun reset() {
        z1.fill(0f)
        z2.fill(0f)
    }

    fun lowPass(fc: Double, q: Double, sr: Double) {
        val w = 2 * PI * fc / sr
        val cw = cos(w)
        val alpha = sin(w) / (2 * q)
        set((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    fun highPass(fc: Double, q: Double, sr: Double) {
        val w = 2 * PI * fc / sr
        val cw = cos(w)
        val alpha = sin(w) / (2 * q)
        set((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    /** Constant 0 dB peak-gain band-pass. */
    fun bandPass(fc: Double, q: Double, sr: Double) {
        val w = 2 * PI * fc / sr
        val alpha = sin(w) / (2 * q)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * cos(w), 1 - alpha)
    }

    fun lowShelf(fc: Double, gainDb: Double, sr: Double) {
        val a = 10.0.pow(gainDb / 40)
        val w = 2 * PI * fc / sr
        val cw = cos(w)
        val alpha = sin(w) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * cw + sa),
            2 * a * ((a - 1) - (a + 1) * cw),
            a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa,
            -2 * ((a - 1) + (a + 1) * cw),
            (a + 1) + (a - 1) * cw - sa,
        )
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / na0).toFloat()
        b1 = (nb1 / na0).toFloat()
        b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat()
        a2 = (na2 / na0).toFloat()
    }

    fun process(x: Float, c: Int): Float {
        val y = b0 * x + z1[c]
        z1[c] = b1 * x - a1 * y + z2[c]
        z2[c] = b2 * x - a2 * y
        return y
    }
}

/**
 * Schroeder reverb: four parallel damped comb filters into two all-passes. Each comb's
 * feedback g = 10^(-3·D/RT60), so a sound decays by 60 dB in RT60 seconds (g^k per pass).
 */
private class Reverb(private val sampleRate: Int, channels: Int) {
    private val combLen = Array(channels) { c -> IntArray(4) { (COMB_MS[it] * sampleRate / 1000).toInt() + c * 23 } }
    private val combBuf = Array(channels) { c -> Array(4) { FloatArray(combLen[c][it]) } }
    private val combIdx = Array(channels) { IntArray(4) }
    private val combLp = Array(channels) { FloatArray(4) }
    private val combG = Array(channels) { FloatArray(4) }
    private val apBuf = Array(channels) { Array(2) { FloatArray((AP_MS[it] * sampleRate / 1000).toInt()) } }
    private val apIdx = Array(channels) { IntArray(2) }
    private var rt60 = -1f

    fun setRt60(seconds: Float) {
        if (seconds == rt60) return
        rt60 = seconds
        for (c in combLen.indices) for (i in 0 until 4) {
            combG[c][i] = 10.0.pow(-3.0 * combLen[c][i] / sampleRate / seconds).toFloat()
        }
    }

    fun process(x: Float, c: Int): Float {
        var sum = 0f
        for (i in 0 until 4) {
            val buf = combBuf[c][i]
            val idx = combIdx[c][i]
            val y = buf[idx]
            combLp[c][i] = y * 0.8f + combLp[c][i] * 0.2f
            buf[idx] = x + combG[c][i] * combLp[c][i]
            combIdx[c][i] = (idx + 1) % buf.size
            sum += y
        }
        var v = sum * 0.25f
        for (i in 0 until 2) {
            val buf = apBuf[c][i]
            val idx = apIdx[c][i]
            val delayed = buf[idx]
            val out = -AP_G * v + delayed
            buf[idx] = v + AP_G * out
            apIdx[c][i] = (idx + 1) % buf.size
            v = out
        }
        return v
    }

    fun clear() {
        combBuf.forEach { ch -> ch.forEach { it.fill(0f) } }
        combLp.forEach { it.fill(0f) }
        apBuf.forEach { ch -> ch.forEach { it.fill(0f) } }
    }

    private companion object {
        val COMB_MS = doubleArrayOf(29.7, 37.1, 41.1, 43.7)
        val AP_MS = doubleArrayOf(5.0, 1.7)
        const val AP_G = 0.7f
    }
}
