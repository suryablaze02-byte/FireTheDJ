package com.autodj.dj.fx

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Classic DJ sound effects, synthesised so they need no samples and always sit on the beat:
 *
 * - **Siren:** a saturated tone swooping between ~500 and ~1000 Hz (dub siren).
 * - **Air horn:** two detuned sawtooths a fifth apart, blasting "bwaa-bwaa-bwaa-bwaaaa" on 8ths.
 * - **Laser:** zaps whose pitch dives from ~2.5 kHz, repeating every [FxEvent.p1] beats.
 * - **Riser:** noise through a band-pass sweeping 300 Hz → 6 kHz plus a rising tone, swelling into the end.
 * - **Impact:** a sub "boom" diving 140 → 40 Hz with a short noise crack.
 * - **Snare roll:** snare hits on quarters, then 8ths, then 16ths, rising in pitch and level.
 * - **Reverse cymbal:** a crash played backwards, swelling into the end of the event.
 * - **Crash:** a cymbal hit: bright noise plus inharmonic metal partials, decaying.
 * - **Sub drop:** a long, clean 808-style boom sliding 70 → 30 Hz.
 * - **Downlifter:** noise sweeping down from ~6 kHz and fading: the release after a drop.
 * - **Whoosh:** a noise sweep up and back down, peaking mid-event: a transition swoosh.
 * - **Gunshot:** a crack, a thump and a tail, one every [FxEvent.p1] beats ("pull up").
 *
 * Each effect has its own oscillators and filters, so effects that overlap (a horn on the
 * same beat as an impact) don't garble each other.
 */
class SfxSynth(sampleRate: Int) {
    private val sr = sampleRate.toDouble()
    private val noise = Random(7)

    private class Voice {
        var phase1 = 0.0
        var phase2 = 0.0
        var lp = 0f
        var gain = 0f
        var hpState = 0f
        val partials = DoubleArray(METAL_HZ.size)
        val bandPass = Biquad(1)
    }

    private val voices = HashMap<FxType, Voice>()

    private fun voice(type: FxType) = voices.getOrPut(type) { Voice() }

    fun reset() {
        voices.clear()
    }

    /** Per-block setup (filter coefficients) for the effects active around [t]. */
    fun prepare(events: List<FxEvent>, t: Double) {
        for (e in events) {
            val x = ((t - e.start) / (e.end - e.start)).coerceIn(0.0, 1.0)
            val fc = when (e.type) {
                FxType.RISER -> 300.0 * 20.0.pow(x)
                FxType.DOWNLIFTER -> 6000.0 * (1.0 / 20.0).pow(x)
                FxType.WHOOSH -> 400.0 * 15.0.pow(sin(PI * x))
                FxType.SNARE_ROLL -> 1800.0
                else -> continue
            }
            val q = if (e.type == FxType.SNARE_ROLL) 0.8 else 4.0
            voice(e.type).bandPass.bandPass(min(fc, 0.45 * sr), q, sr)
        }
    }

    /** One mono sample of all active effects at time [t]. */
    fun sample(events: List<FxEvent>, t: Double, beatSec: Double): Float {
        var out = 0.0
        for (e in events) {
            val x = t - e.start
            val left = e.end - t
            val len = e.end - e.start
            val u = (x / len).coerceIn(0.0, 1.0)
            val edge = min(1.0, min(x / 0.01, left / 0.03)) // click-free start and end
            val v = voice(e.type)
            out += GAIN * e.amount(t) * edge * when (e.type) {
                FxType.SIREN -> siren(v, x, if (e.p1 > 0) e.p1.toDouble() else 3.5)
                FxType.HORN -> horn(v, x / beatSec)
                FxType.LASER -> laser(v, x, (if (e.p1 > 0) e.p1 else 0.5f) * beatSec)
                FxType.RISER -> riser(v, u)
                FxType.IMPACT -> impact(v, x)
                FxType.SNARE_ROLL -> snareRoll(v, x / beatSec, len / beatSec, u, beatSec)
                FxType.REVERSE_CYMBAL -> reverseCymbal(v, u)
                FxType.CRASH -> crash(v, x)
                FxType.SUB_DROP -> subDrop(v, x)
                FxType.DOWNLIFTER -> downlifter(v, u)
                FxType.WHOOSH -> whoosh(v, u)
                FxType.GUNSHOT -> gunshot(v, x % ((if (e.p1 > 0) e.p1 else 1f) * beatSec))
                else -> 0.0
            }
        }
        return out.toFloat()
    }

    private fun white() = noise.nextFloat() * 2 - 1

    /** White noise with the lows taken out (one-pole high-pass): hiss, cymbal and air. */
    private fun hiss(v: Voice, coef: Float = 0.2f): Float {
        val n = white()
        v.hpState += coef * (n - v.hpState)
        return n - v.hpState
    }

    /** Square-wave partials at inharmonic ratios: the clang of a cymbal or bell. */
    private fun metal(v: Voice): Double {
        var s = 0.0
        for (i in METAL_HZ.indices) {
            v.partials[i] = (v.partials[i] + METAL_HZ[i] / sr) % 1.0
            s += if (v.partials[i] < 0.5) 1.0 else -1.0
        }
        return s / METAL_HZ.size
    }

    private fun siren(v: Voice, x: Double, rate: Double): Double {
        val lfo = 0.5 + 0.5 * sin(2 * PI * rate * x)
        v.phase1 = (v.phase1 + (500 + 500 * lfo) / sr) % 1.0
        return 0.6 * tanh(3 * sin(2 * PI * v.phase1))
    }

    /** [u]: beats since the horn started. */
    private fun horn(v: Voice, u: Double): Double {
        val on = u < 0.375 || (u >= 0.5 && u < 0.875) || (u >= 1.0 && u < 1.375) || u >= 1.5
        v.gain += ((if (on) 1f else 0f) - v.gain) * 0.004f
        val vibrato = 1 + 0.008 * sin(2 * PI * 6 * u)
        val droop = 1 - 0.04 * (u - 1.5).coerceAtLeast(0.0)
        val f = 290.0 * vibrato * droop
        v.phase1 = (v.phase1 + f / sr) % 1.0
        v.phase2 = (v.phase2 + 1.5 * f * 1.003 / sr) % 1.0
        val saw = (2 * (v.phase1 - floor(v.phase1 + 0.5)) + 2 * (v.phase2 - floor(v.phase2 + 0.5))) * 0.5
        v.lp += (saw.toFloat() - v.lp) * 0.35f
        return 0.7 * v.lp * v.gain
    }

    private fun laser(v: Voice, x: Double, period: Double): Double {
        val z = x % period
        v.phase1 = (v.phase1 + (100 + 2400 * exp(-z * 30)) / sr) % 1.0
        return 0.7 * sin(2 * PI * v.phase1) * exp(-z * 10)
    }

    /** [x]: 0..1 through the riser. */
    private fun riser(v: Voice, x: Double): Double {
        val bp = v.bandPass.process(white(), 0)
        v.phase1 = (v.phase1 + 150 * 8.0.pow(x) / sr) % 1.0
        val amp = 0.15 + 0.85 * x * x
        return (0.9 * bp + 0.3 * sin(2 * PI * v.phase1)) * amp
    }

    private fun impact(v: Voice, x: Double): Double {
        v.phase1 = (v.phase1 + (40 + 100 * exp(-x * 6)) / sr) % 1.0
        return sin(2 * PI * v.phase1) * exp(-x * 1.2) + 0.5 * white() * exp(-x * 30)
    }

    /**
     * [b]: beats since the roll started, [lenBeats] its length, [u] 0..1 through it. Quarters for
     * the first half, 8ths for the next quarter, 16ths to the end.
     */
    private fun snareRoll(v: Voice, b: Double, lenBeats: Double, u: Double, beatSec: Double): Double {
        val (segStart, step) = when {
            u < 0.5 -> 0.0 to 1.0
            u < 0.75 -> lenBeats * 0.5 to 0.5
            else -> lenBeats * 0.75 to 0.25
        }
        val since = ((b - segStart) % step) * beatSec
        val hit = exp(-since * 28)
        val tone = 180.0 + 220.0 * u
        v.phase1 = (v.phase1 + tone / sr) % 1.0
        val body = sin(2 * PI * v.phase1) * exp(-since * 45)
        val rattle = v.bandPass.process(white(), 0) * 2.2
        return (0.45 + 0.55 * u) * (rattle * hit + 0.5 * body)
    }

    /** [x]: 0..1 through the swell; it peaks right at the end, where the drop lands. */
    private fun reverseCymbal(v: Voice, x: Double): Double {
        val amp = x.pow(3.0)
        return amp * (0.75 * hiss(v) + 0.35 * metal(v))
    }

    private fun crash(v: Voice, x: Double): Double {
        val amp = exp(-x * 1.6) * min(1.0, x / 0.002)
        return amp * (0.75 * hiss(v) + 0.35 * metal(v))
    }

    private fun subDrop(v: Voice, x: Double): Double {
        v.phase1 = (v.phase1 + (30 + 40 * exp(-x * 1.8)) / sr) % 1.0
        val amp = exp(-x * 0.9) * min(1.0, x / 0.005)
        return 0.9 * tanh(1.5 * sin(2 * PI * v.phase1)) * amp
    }

    /** [x]: 0..1 through the fall. */
    private fun downlifter(v: Voice, x: Double): Double =
        v.bandPass.process(white(), 0) * 1.1 * (1 - x) * (1 - x)

    /** [x]: 0..1 through the swoosh. */
    private fun whoosh(v: Voice, x: Double): Double =
        v.bandPass.process(white(), 0) * 1.2 * sin(PI * x).pow(2.0)

    /** [x]: seconds since this shot. */
    private fun gunshot(v: Voice, x: Double): Double {
        v.phase1 = (v.phase1 + (55 + 90 * exp(-x * 25)) / sr) % 1.0
        val crack = white() * exp(-x * 60)
        val thump = sin(2 * PI * v.phase1) * exp(-x * 9)
        v.lp += (white() - v.lp) * 0.08f
        val tail = v.lp * 2.0 * exp(-x * 5)
        return 0.8 * crack + 0.7 * thump + 0.4 * tail
    }

    private companion object {
        /** Effects sit roughly level with the music (the deck output is soft-limited). */
        const val GAIN = 0.8

        val METAL_HZ = doubleArrayOf(3170.0, 4230.0, 5410.0, 6870.0, 8190.0)
    }
}
