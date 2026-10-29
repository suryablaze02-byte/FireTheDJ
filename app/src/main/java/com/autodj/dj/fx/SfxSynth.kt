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
 * Indian regional sounds, played as patterns on the track's own beat ([Drum] says which part):
 *
 * - **Dappu** (Telugu teenmaar frame drum): open "DHA" strokes (a ~185 Hz membrane bending down,
 *   with a bright slap) and muted "ka" slaps in a 6/8 triplet feel; the fill doubles up into the
 *   bar line, and the hit is three accented strokes, the "teen maar".
 * - **Marfa** (Hyderabadi wedding drums): a low pot drum and daff slaps in a driving LOW-slap-slap
 *   cell with steel-pot clicks on the off-beats, rolling faster into the cut.
 * - **Dhol** (Punjabi bhangra): the bass head booming on beats one and four of the triplet chaal,
 *   the treble head cracking in between; the fill is all treble triplets, then a unison hit.
 * - **Thavil** (South Indian barrel drum): hard, high stick cracks, "ta-ka-dhim".
 * - **Nadaswaram** (South Indian wedding reed): a nasal reed voice that slides up into each note
 *   (gamaka), climbing four notes of the track's own key to a held, wavering top note.
 *
 * Latin and Arabic party sounds, on the same beat patterns:
 *
 * - **Whistle** (samba apito): a pea whistle warbling around 3 kHz, calling "long, short-short".
 * - **Timbales:** two ringing metal-shelled drums and a cascara stick on the shell; the fill
 *   is the 16th-note roll every salsa and reggaeton break ends on.
 * - **Cowbell:** two square tones (540 and 800 Hz) through a band-pass, the 808 bell.
 * - **Güira** (merengue scraper): a long scrape on the beat, two short ones after it.
 * - **Shaker:** bright noise with a soft attack in 16ths, the "and" accented.
 * - **Claps:** three quick noise bursts and a short room tail, on two and four.
 * - **Darbuka** (Arabic goblet drum): the deep "dum" in the middle of the head and the sharp
 *   "tek" on its rim, playing maqsum ("dum-tek, tek, dum, tek").
 * - **Mizmar** (Egyptian wedding reed): the reed voice brighter, climbing the hijaz scale.
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
        val bandPass2 = Biquad(1)

        /** A percussion event's strokes, worked out once per event. */
        var strokesFor: FxEvent? = null
        var strokes: List<Stroke> = emptyList()
    }

    /** One drum stroke [at] beats into the event: [kind] names the stroke, [vel] its strength. */
    private class Stroke(val at: Double, val kind: Char, val vel: Float)

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
                FxType.DAPPU -> 2200.0
                FxType.MARFA -> 1800.0
                FxType.DHOL -> 3000.0
                FxType.THAVIL -> 3200.0
                FxType.NADASWARAM -> {
                    // Two formants make the reed's nasal "aa".
                    voice(e.type).bandPass2.bandPass(min(2600.0, 0.45 * sr), 5.0, sr)
                    1100.0
                }
                FxType.MIZMAR -> {
                    voice(e.type).bandPass2.bandPass(min(3200.0, 0.45 * sr), 4.0, sr)
                    1500.0
                }
                FxType.WHISTLE -> 3000.0
                FxType.TIMBALES -> 2500.0
                FxType.COWBELL -> 2000.0
                FxType.GUIRA -> 5000.0
                FxType.SHAKER -> 6500.0
                FxType.CLAPS -> 1200.0
                FxType.DARBUKA -> 3500.0
                else -> continue
            }
            val q = when (e.type) {
                FxType.SNARE_ROLL, FxType.SHAKER -> 0.8
                FxType.DAPPU, FxType.MARFA, FxType.CLAPS, FxType.DARBUKA -> 1.2
                FxType.DHOL, FxType.THAVIL -> 1.5
                FxType.TIMBALES, FxType.COWBELL, FxType.GUIRA -> 1.0
                FxType.WHISTLE -> 3.0
                FxType.NADASWARAM -> 5.0
                else -> 4.0
            }
            voice(e.type).bandPass.bandPass(min(fc, 0.45 * sr), q, sr)
        }
    }

    /**
     * One mono sample of all active effects at time [t]. [duck] (0..1) is the kick sidechain,
     * so the effects pump with the track.
     */
    fun sample(events: List<FxEvent>, t: Double, beatSec: Double, duck: Float = 1f): Float {
        var out = 0.0
        for (e in events) {
            // Events are picked per block, so a sample can fall just outside one: silent there.
            if (t < e.start || t >= e.end) continue
            val x = t - e.start
            val left = e.end - t
            val len = e.end - e.start
            val u = (x / len).coerceIn(0.0, 1.0)
            val edge = min(1.0, min(x / 0.01, left / 0.03)) // click-free start and end
            val v = voice(e.type)
            val pump = duck
            out += GAIN * e.amount(t) * edge * pump * when (e.type) {
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
                FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL, FxType.WHISTLE, FxType.TIMBALES, FxType.COWBELL,
                FxType.GUIRA, FxType.SHAKER, FxType.CLAPS, FxType.DARBUKA -> drum(v, e, x, beatSec)
                FxType.NADASWARAM -> reed(v, x, left, beatSec, if (e.p1 > 0) e.p1.toDouble() else 330.0, if (e.p2 >= 1f) MINOR else MAJOR)
                FxType.MIZMAR -> reed(v, x, left, beatSec, if (e.p1 > 0) e.p1.toDouble() else 330.0, HIJAZ)
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

    /** All strokes of a percussion event, in beats from its start. */
    private fun strokesOf(e: FxEvent, beatSec: Double): List<Stroke> {
        val lenBeats = (e.end - e.start) / beatSec
        val out = mutableListOf<Stroke>()
        val fillBeats = when (e.p1) {
            Drum.HIT -> 0.0
            Drum.FILL_AND_HIT -> 4.0
            else -> lenBeats
        }
        // Groove bars, then the fill bar ending on the bar line, louder as it goes.
        val bars = maxOf(1, Math.round(fillBeats / 4).toInt())
        if (fillBeats > 0) {
            for (b in 0 until bars) {
                val last = b == bars - 1
                val from = fillBeats - (bars - b) * 4.0
                for (s in barStrokes(e.type, last)) {
                    val at = from + s.at
                    if (at < 0) continue
                    val swell = (0.65 + 0.35 * at / fillBeats).toFloat()
                    out += Stroke(at, s.kind, s.vel * swell)
                }
            }
        }
        if (e.p1 != Drum.FILL) for (s in hitStrokes(e.type)) out += Stroke(fillBeats + s.at, s.kind, s.vel)
        return out.sortedBy { it.at }
    }

    /** One bar of the groove ([fill] = false) or the fill into the bar line. */
    private fun barStrokes(type: FxType, fill: Boolean): List<Stroke> = when (type) {
        // 12 triplet slots per bar, 6/8 feel.
        FxType.DAPPU -> slots(if (fill) "D.kD.kDkDkDk" else "D.kD.kD.kD.k", 3)
        FxType.DHOL -> if (fill) {
            listOf(Stroke(0.0, 'x', 1.1f)) + (0 until 12).map { Stroke(it / 3.0, 't', (0.55 + 0.5 * it / 11).toFloat()) }
        } else {
            // Chaal: bass head on beats one and four, treble in between.
            slots("xttx.txttxtt", 3).map { if (it.kind == 'x' && (it.at == 0.0 || it.at == 3.0)) Stroke(it.at, 'x', 1.2f) else it }
        }
        FxType.MARFA -> if (fill) {
            // Two beats of the cell, then slaps rolling in 16ths and 32nds into the cut.
            slots("LssL", 2) + (0 until 4).map { Stroke(2 + it / 4.0, 's', 0.7f + 0.05f * it) } +
                (0 until 8).map { Stroke(3 + it / 8.0, 's', 0.75f + 0.04f * it) }
        } else {
            slots("LssLssLs", 2) + (0 until 4).map { Stroke(it + 0.5, 'c', 0.6f) }
        }
        // Stick strokes in 16ths, accented in threes.
        FxType.THAVIL -> (0 until 16).map { Stroke(it / 4.0, 'T', if (it % 3 == 0 || it == 15) 1.05f else 0.55f) }
        // "Long, short-short" calls; the fill blows 8ths, then 16ths.
        FxType.WHISTLE -> slots(if (fill) "w.w.w.w.wwwwwwww" else "W...w.w.W...w.w.", 4)
        // Cascara on the shell with the low drum answering; the fill rolls 16ths, the low drum closing each beat.
        FxType.TIMBALES -> if (fill) {
            (0 until 16).map { Stroke(it / 4.0, if (it % 4 == 3) 'l' else 'h', (0.55 + 0.5 * it / 15).toFloat()) }
        } else {
            slots("r.rr.rlr", 2)
        }
        FxType.COWBELL -> slots(if (fill) "BbbbBbbbBbBbBbBb" else "B.bbB.b.", if (fill) 4 else 2)
        FxType.GUIRA -> slots(if (fill) "L.ssL.ssssssssss" else "L.ssL.ssL.ssL.ss", 4)
        FxType.SHAKER -> if (fill) {
            (0 until 12).map { Stroke(it / 4.0, 's', (0.5 + 0.4 * it / 11).toFloat()) } +
                (0 until 8).map { Stroke(3 + it / 8.0, 's', 0.9f + 0.03f * it) }
        } else {
            slots("ssSsssSsssSsssSs", 4)
        }
        // On two and four; the fill claps 8ths, then 16ths.
        FxType.CLAPS -> slots(if (fill) "C.C.C.C.CCCCCCCC" else ".C.C", if (fill) 4 else 1)
        // Maqsum; the fill is a tek-ka roll in 16ths, then 32nds.
        FxType.DARBUKA -> if (fill) {
            (0 until 12).map { Stroke(it / 4.0, if (it % 2 == 0) 'T' else 'k', (0.55 + 0.35 * it / 11).toFloat()) } +
                (0 until 8).map { Stroke(3 + it / 8.0, if (it % 2 == 0) 'T' else 'k', 0.85f + 0.03f * it) }
        } else {
            slots("DT.TD.T.", 2)
        }
        else -> emptyList()
    }

    /** What lands on the bar line. */
    private fun hitStrokes(type: FxType): List<Stroke> = when (type) {
        // "Teen maar": three accented strokes on beats one, two and three.
        FxType.DAPPU -> listOf(Stroke(0.0, 'A', 1.25f), Stroke(1.0, 'A', 1.15f), Stroke(2.0, 'A', 1.3f))
        FxType.MARFA -> listOf(Stroke(0.0, 'X', 1.3f), Stroke(0.0, 'c', 0.8f))
        FxType.DHOL -> listOf(Stroke(0.0, 'X', 1.3f))
        // "Ta-ka-dhim".
        FxType.THAVIL -> listOf(Stroke(0.0, 'T', 1f), Stroke(0.5, 'T', 0.7f), Stroke(0.75, 'T', 1.15f))
        FxType.WHISTLE -> listOf(Stroke(0.0, 'X', 1.2f))
        FxType.TIMBALES -> listOf(Stroke(0.0, 'X', 1.3f))
        FxType.COWBELL -> listOf(Stroke(0.0, 'B', 1.3f))
        FxType.GUIRA, FxType.SHAKER -> listOf(Stroke(0.0, 'L', 1.2f))
        FxType.CLAPS -> listOf(Stroke(0.0, 'C', 1.3f))
        // "Dum, tek-tek".
        FxType.DARBUKA -> listOf(Stroke(0.0, 'D', 1.3f), Stroke(0.5, 'T', 1f), Stroke(0.75, 'T', 1.1f))
        else -> emptyList()
    }

    /** A pattern string, one character per slot ('.' = rest), [perBeat] slots to a beat. */
    private fun slots(pattern: String, perBeat: Int) =
        pattern.mapIndexedNotNull { i, c -> if (c == '.') null else Stroke(i / perBeat.toDouble(), c, if (c.isUpperCase()) 1f else 0.85f) }

    /** Every stroke of a percussion event still sounding at [x] seconds in, summed. */
    private fun drum(v: Voice, e: FxEvent, x: Double, beatSec: Double): Double {
        if (v.strokesFor !== e) {
            v.strokesFor = e
            v.strokes = strokesOf(e, beatSec)
        }
        var tone = 0.0
        var slap = 0.0
        var clang = 0.0
        var bell = 0.0
        var blow = 0.0
        for (s in v.strokes) {
            val since = x - s.at * beatSec
            if (since < 0) break
            if (since > 0.7) continue
            val atk = min(1.0, since / 0.001)
            val vel = s.vel * atk
            when (e.type) {
                FxType.DAPPU -> {
                    if (s.kind != 'k') tone += vel * 0.9 * membrane(since, 185.0, 158.0, 0.04, 0.12)
                    slap += vel * (if (s.kind == 'k') 0.75 else 0.8) * exp(-since / (if (s.kind == 'k') 0.025 else 0.03))
                }
                FxType.MARFA -> {
                    if (s.kind == 'L' || s.kind == 'X') tone += vel * membrane(since, 98.0, 75.0, 0.06, 0.25)
                    if (s.kind == 's' || s.kind == 'X') {
                        tone += vel * 0.5 * membrane(since, 235.0, 205.0, 0.03, 0.06)
                        slap += vel * 0.7 * exp(-since / 0.028)
                    }
                    if (s.kind == 'c') clang += vel * 0.35 * exp(-since / 0.02)
                }
                FxType.DHOL -> {
                    if (s.kind == 'x' || s.kind == 'X') tone += vel * membrane(since, 88.0, 62.0, 0.05, 0.30)
                    if (s.kind == 't' || s.kind == 'X') {
                        slap += vel * 0.8 * exp(-since / 0.04)
                        tone += vel * 0.35 * membrane(since, 520.0, 500.0, 0.02, 0.05)
                    }
                }
                FxType.THAVIL -> {
                    slap += vel * 0.9 * exp(-since / 0.02)
                    tone += vel * 0.6 * membrane(since, 360.0, 330.0, 0.02, 0.08)
                }
                FxType.WHISTLE -> {
                    val dur = when (s.kind) {
                        'X' -> min(0.6, beatSec)
                        'W' -> 0.4 * beatSec
                        else -> 0.16 * beatSec
                    }
                    if (since < dur) blow = maxOf(blow, vel * min(1.0, (dur - since) / 0.01))
                }
                FxType.TIMBALES -> {
                    // 'h' high shell, 'l' low shell, 'r' the stick on the shell's side, 'X' both shells and the rim.
                    if (s.kind == 'h' || s.kind == 'X') tone += vel * 0.8 * membrane(since, 560.0, 520.0, 0.01, 0.16)
                    if (s.kind == 'l' || s.kind == 'X') tone += vel * 0.8 * membrane(since, 390.0, 360.0, 0.01, 0.2)
                    if (s.kind == 'r') clang += vel * 0.3 * exp(-since / 0.012)
                    if (s.kind == 'X') clang += vel * 0.35 * exp(-since / 0.25)
                    slap += vel * (if (s.kind == 'r') 0.5 else 0.6) * exp(-since / 0.012)
                }
                FxType.COWBELL -> bell += vel * (0.6 * exp(-since / 0.02) + 0.5 * exp(-since / (if (s.kind == 'B') 0.16 else 0.09)))
                FxType.CLAPS -> slap += vel * clap(since)
                FxType.SHAKER -> slap += vel * 0.7 * min(1.0, since / 0.012) * exp(-since / (if (s.kind == 'L') 0.18 else 0.045))
                FxType.GUIRA -> {
                    val dur = (if (s.kind == 'L') 0.4 else 0.12) * beatSec
                    val held = if (since < dur) 1.0 else exp(-(since - dur) / 0.01)
                    // The scraper's ridges ticking past the stick.
                    slap += vel * 0.6 * held * (0.55 + 0.45 * sin(2 * PI * 110 * since)) * min(1.0, since / 0.005)
                }
                FxType.DARBUKA -> if (s.kind == 'D') {
                    tone += vel * membrane(since, 120.0, 82.0, 0.03, 0.28)
                } else {
                    // 'T' tek, 'k' the softer ka of the other hand.
                    tone += vel * (if (s.kind == 'T') 0.55 else 0.35) * membrane(since, 640.0, 590.0, 0.01, 0.035)
                    slap += vel * (if (s.kind == 'T') 0.8 else 0.5) * exp(-since / 0.012)
                }
                else -> Unit
            }
        }
        val noise = if (slap > 1e-4) v.bandPass.process(white(), 0) * 2.2 * slap else 0.0
        val metal = if (clang > 1e-4) metal(v) * clang else 0.0
        val bells = if (bell > 1e-4) cowbell(v) * bell else 0.0
        val whistle = if (blow > 1e-4) whistle(v, x) * blow else 0.0
        return 0.75 * tanh(1.3 * tone) + noise + metal + bells + whistle
    }

    /** Three quick bursts (hands not quite together) and a short room tail; times the band-passed noise. */
    private fun clap(since: Double): Double {
        var e = 0.0
        for (k in 0 until 3) {
            val d = since - k * 0.011
            if (d >= 0) e += exp(-d / 0.004)
        }
        if (since > 0.022) e += 0.7 * exp(-(since - 0.022) / 0.08)
        return 0.8 * e
    }

    /** The 808 cowbell: two square tones through the voice's band-pass. */
    private fun cowbell(v: Voice): Double {
        v.phase1 = (v.phase1 + 540 / sr) % 1.0
        v.phase2 = (v.phase2 + 800 / sr) % 1.0
        val sq = (if (v.phase1 < 0.5) 0.5f else -0.5f) + (if (v.phase2 < 0.5) 0.5f else -0.5f)
        return 1.4 * v.bandPass.process(sq, 0)
    }

    /** A pea whistle: a tone warbling around 3 kHz as the pea spins, with some breath. */
    private fun whistle(v: Voice, x: Double): Double {
        val warble = sin(2 * PI * 28 * x)
        v.phase1 = (v.phase1 + (3000 + 180 * warble) / sr) % 1.0
        val breath = v.bandPass.process(white(), 0)
        return 0.45 * (0.75 + 0.25 * warble) * sin(2 * PI * v.phase1) + 0.12 * breath
    }

    /**
     * A struck drum head [since] seconds after the stroke: pitch bending from [f0] down to [f1]
     * (time constant [tau]), decaying with time constant [decay]. Closed form, so strokes overlap freely.
     */
    private fun membrane(since: Double, f0: Double, f1: Double, tau: Double, decay: Double): Double {
        val phase = f1 * since + (f0 - f1) * tau * (1 - exp(-since / tau))
        return sin(2 * PI * phase) * exp(-since / decay)
    }

    /**
     * The nadaswaram figure: four notes of the key (half a beat each), each slid up into from
     * 1.5 semitones below, then the octave held with a vibrato that grows; [left]: seconds to the end.
     */
    private fun reed(v: Voice, x: Double, left: Double, beatSec: Double, tonic: Double, steps: IntArray): Double {
        val b = x / beatSec
        val idx = floor(b / 0.5).toInt().coerceIn(0, 4)
        val noteStart = idx * 0.5 * beatSec
        val since = x - noteStart
        val semis = if (idx < 4) steps[idx].toDouble() else 12.0
        val glide = -1.5 * exp(-since / 0.05)
        val vibrato = (if (idx < 4) 0.08 else 0.3 * min(1.0, since / 0.4)) * sin(2 * PI * 5.5 * x)
        val f = tonic * 2.0.pow((semis + glide + vibrato) / 12)
        v.phase1 = (v.phase1 + f / sr) % 1.0
        val saw = 2 * (v.phase1 - floor(v.phase1 + 0.5))
        val voiced = 0.9 * v.bandPass.process(saw.toFloat(), 0) + 0.6 * v.bandPass2.process(saw.toFloat(), 0) + 0.12 * saw
        // Tongued notes: a quick attack and a slight lift-off before the next one.
        val shape = if (idx < 4) 1 - 0.35 * (since / (0.5 * beatSec)).coerceIn(0.0, 1.0).pow(3.0) else 1.0
        return 1.6 * voiced * min(1.0, since / 0.015) * shape * min(1.0, left / 0.2)
    }


    private companion object {
        /** Effects sit roughly level with the music (the deck output is soft-limited). */
        const val GAIN = 1.0

        val METAL_HZ = doubleArrayOf(3170.0, 4230.0, 5410.0, 6870.0, 8190.0)

        /** The reed's four notes up from the tonic, in a major key, a minor one, and maqam hijaz. */
        val MAJOR = intArrayOf(0, 2, 4, 7)
        val MINOR = intArrayOf(0, 3, 5, 7)
        val HIJAZ = intArrayOf(0, 1, 4, 5)
    }
}
