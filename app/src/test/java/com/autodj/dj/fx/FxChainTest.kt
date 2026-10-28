package com.autodj.dj.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class FxChainTest {
    private val sr = 44100

    private fun plan(events: List<FxEvent>, gain: List<Ramp> = emptyList(), beatSec: Double = 0.5) =
        FxPlan(beatSec, 0.0, Lane(gain, 1f), Lane(emptyList(), 1f), events)

    /** Runs mono audio through a chain with [p] and returns the output. */
    private fun run(p: FxPlan, input: FloatArray): FloatArray {
        val chain = FxChain(sr, 1)
        chain.plan = p
        chain.reset(0.0)
        val buf = input.copyOf()
        var i = 0
        while (i < buf.size) {
            val n = minOf(1024, buf.size - i)
            val block = buf.copyOfRange(i, i + n)
            chain.process(block, n)
            block.copyInto(buf, i)
            i += n
        }
        return buf
    }

    private fun sine(freq: Double, seconds: Double, amp: Double = 0.5, untilSec: Double = seconds) =
        FloatArray((seconds * sr).toInt()) { i -> if (i < untilSec * sr) (amp * sin(2 * PI * freq * i / sr)).toFloat() else 0f }

    private fun rms(x: FloatArray, fromSec: Double, toSec: Double): Double {
        val a = (fromSec * sr).toInt()
        val b = (toSec * sr).toInt()
        return sqrt((a until b).sumOf { x[it].toDouble() * x[it] } / (b - a))
    }

    private fun peak(x: FloatArray, fromSec: Double, toSec: Double) =
        ((fromSec * sr).toInt() until (toSec * sr).toInt()).maxOf { abs(x[it]) }

    @Test
    fun constantPowerCrossfadeKeepsPower() {
        val out = Ramp(0.0, 1.0, 1f, 0f, Law.CONSTANT_POWER)
        val inn = Ramp(0.0, 1.0, 0f, 1f, Law.CONSTANT_POWER)
        for (t in listOf(0.1, 0.3, 0.5, 0.8)) {
            val a = out.value(t)
            val b = inn.value(t)
            assertEquals(1f, a * a + b * b, 1e-4f)
        }
        assertEquals(0.5f, Ramp(0.0, 1.0, 0f, 1f).value(0.5), 1e-6f)
        assertEquals(0.25f, Ramp(0.0, 1.0, 0f, 1f, Law.POWER).value(0.5), 1e-6f)
    }

    @Test
    fun echoRepeatsDecayByFeedback() {
        // 50 ms burst into a 1/2-beat (0.25 s) echo with g = 0.5, dry cut right after.
        val p = plan(
            listOf(FxEvent(FxType.ECHO, 0.0, 0.05, 1f, 1f, p1 = 0.5f, p2 = 0.5f)),
            gain = listOf(Ramp(0.05, 0.0501, 1f, 0f)),
        )
        val out = run(p, sine(200.0, 1.2, untilSec = 0.05))
        val first = peak(out, 0.25, 0.30)
        val second = peak(out, 0.50, 0.55)
        val third = peak(out, 0.75, 0.80)
        assertEquals(0.85 * 0.5, first.toDouble(), 0.05)
        assertEquals(0.5, (second / first).toDouble(), 0.07)
        assertEquals(0.5, (third / second).toDouble(), 0.07)
    }

    @Test
    fun gateIsSquareOnTheBeatGrid() {
        // Period 1/2 beat = 0.25 s: on for the first half of each period, muted for the second.
        val p = plan(listOf(FxEvent(FxType.GATE, 0.0, 1.0, 1f, 1f, p1 = 0.5f)))
        val out = run(p, sine(1000.0, 1.0))
        assertTrue(rms(out, 0.02, 0.10) > 0.3)
        assertTrue(rms(out, 0.15, 0.23) < 0.01)
        assertTrue(rms(out, 0.27, 0.35) > 0.3)
    }

    @Test
    fun highPassRemovesBassButKeepsTreble() {
        val p = plan(listOf(FxEvent(FxType.HIGH_PASS, 0.0, 1.0, 1f, 1f)))
        val bass = run(p, sine(100.0, 1.0))
        val treble = run(p, sine(6000.0, 1.0))
        assertTrue(rms(bass, 0.3, 0.9) < 0.01)
        assertTrue(rms(treble, 0.3, 0.9) > 0.3)
    }

    @Test
    fun loopRollRepeatsTheCapturedSlice() {
        // Loop of 1/2 beat = 0.1 s, captured from 0.1 s.
        val rnd = Random(3)
        val input = FloatArray(sr) { (rnd.nextFloat() - 0.5f) * 0.5f }
        val p = plan(listOf(FxEvent(FxType.LOOP_ROLL, 0.1, 1.0, p1 = 0.5f)), beatSec = 0.2)
        val out = run(p, input)
        val loop = (0.1 * sr).toInt()
        val start = (0.6 * sr).toInt() + 200
        for (i in start until start + 2000) assertEquals(out[i], out[i + loop], 1e-3f)
    }

    @Test
    fun backspinRaceBackwardsThroughRecentAudio() {
        val input = sine(1000.0, 2.0)
        val p = plan(listOf(FxEvent(FxType.BACKSPIN, 1.0, 1.5, p1 = 3f)))
        val out = run(p, input)
        fun crossings(x: FloatArray, from: Double, to: Double) =
            ((from * sr).toInt() until (to * sr).toInt()).count { (x[it] >= 0) != (x[it + 1] >= 0) }
        val ratio = crossings(out, 1.0, 1.02).toDouble() / crossings(input, 0.5, 0.52)
        assertTrue("speed ratio $ratio", ratio in 2.4..3.2)
        // The platter slows to a stop, so it fades out.
        assertTrue(rms(out, 1.45, 1.5) < rms(out, 1.0, 1.05) * 0.4)
    }

    @Test
    fun reverbDecaysSixtyDecibelsInRt60() {
        val p = plan(
            listOf(FxEvent(FxType.REVERB, 0.0, 0.01, 1f, 1f, p1 = 1.0f)),
            gain = listOf(Ramp(0.01, 0.0101, 1f, 0f)),
        )
        val out = run(p, sine(1000.0, 1.5, untilSec = 0.01))
        val early = rms(out, 0.05, 0.10)
        val mid = rms(out, 0.30, 0.35)
        val late = rms(out, 1.05, 1.10)
        assertTrue(20 * log10(early / mid) < 40)
        assertTrue(20 * log10(early / late) > 40)
    }

    @Test
    fun lofiQuantisesToFewerBits() {
        // 4 bits: every sample lands on a multiple of 1/8.
        val p = plan(listOf(FxEvent(FxType.LOFI, 0.0, 1.0, 1f, 1f, p1 = 4f, p2 = 1f)))
        val out = run(p, sine(440.0, 0.5, amp = 0.1)) // below the output soft limiter
        for (i in sr / 10 until sr / 2) assertEquals(0.0, abs(out[i] * 8 - round(out[i] * 8)).toDouble(), 1e-4)
    }

    /** Runs stereo audio (separate left/right) and returns (left, right). */
    private fun runStereo(p: FxPlan, l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
        val chain = FxChain(sr, 2)
        chain.plan = p
        chain.reset(0.0)
        val buf = FloatArray(l.size * 2) { if (it % 2 == 0) l[it / 2] else r[it / 2] }
        chain.process(buf, l.size)
        return FloatArray(l.size) { buf[it * 2] } to FloatArray(l.size) { buf[it * 2 + 1] }
    }

    @Test
    fun vocalCutRemovesTheCentreButKeepsBassAndSides() {
        val p = plan(listOf(FxEvent(FxType.VOCAL_CUT, 0.0, 1.0, 1f, 1f)))
        val voice = sine(1000.0, 1.0)
        val (cl, _) = runStereo(p, voice, voice) // centred "vocal"
        assertTrue(rms(cl, 0.3, 0.9) < 0.1 * rms(voice, 0.3, 0.9))
        val bass = sine(60.0, 1.0)
        val (bl, _) = runStereo(p, bass, bass) // centred kick/bass
        assertTrue(rms(bl, 0.3, 0.9) > 0.8 * rms(bass, 0.3, 0.9))
        val wide = sine(1000.0, 1.0)
        val (wl, _) = runStereo(p, wide, FloatArray(wide.size) { -wide[it] }) // side content
        assertTrue(rms(wl, 0.3, 0.9) > 0.9 * rms(wide, 0.3, 0.9))
    }

    @Test
    fun vocalIsolateKeepsOnlyTheCentreVocalBand() {
        val p = plan(listOf(FxEvent(FxType.VOCAL_ISOLATE, 0.0, 1.0, 1f, 1f)))
        val voice = sine(1000.0, 1.0)
        val (vl, _) = runStereo(p, voice, voice)
        assertTrue(rms(vl, 0.3, 0.9) > 0.8 * rms(voice, 0.3, 0.9))
        val bass = sine(60.0, 1.0)
        val (bl, _) = runStereo(p, bass, bass)
        assertTrue(rms(bl, 0.3, 0.9) < 0.2 * rms(bass, 0.3, 0.9))
        val (sl, _) = runStereo(p, voice, FloatArray(voice.size) { -voice[it] })
        assertTrue(rms(sl, 0.3, 0.9) < 0.01)
    }

    @Test
    fun soundEffectsAreAudibleOnTopOfSilence() {
        for (type in FxType.entries.filter { it.isSfx }) {
            val p = plan(listOf(FxEvent(type, 0.1, 0.9, 1f, 1f)))
            val out = run(p, FloatArray(sr)) // silent deck
            assertTrue("$type silent", rms(out, 0.2, 0.8) > 0.02)
            assertTrue("$type leaks before", rms(out, 0.0, 0.09) < 1e-4)
            assertTrue("$type clips", out.maxOf { abs(it) } < 1f)
        }
    }

    @Test
    fun loopsAndScratchesDoNotClick() {
        // A steady tone through a roll and a scratch: no sample-to-sample jump bigger than the tone's own.
        val input = sine(220.0, 2.0, amp = 0.5)
        val p = plan(
            listOf(
                FxEvent(FxType.LOOP_ROLL, 0.3, 0.6, p1 = 0.5f),
                FxEvent(FxType.LOOP_ROLL, 0.6, 0.8, p1 = 0.25f),
                FxEvent(FxType.SCRATCH, 1.0, 1.5, p1 = 0.5f, p2 = 0.25f),
                FxEvent(FxType.BRAKE, 1.6, 1.8),
            ),
            beatSec = 0.4,
        )
        val out = run(p, input)
        val natural = (1 until input.size).maxOf { abs(input[it] - input[it - 1]) }
        // Clicks happen where the source switches: event edges, the roll's first wrap (0.3 + 0.2 s)
        // and the scratch's switch from catching to scratching (1.0 + 0.2 s). (Mid-scratch the audio
        // runs fast, so bigger sample steps there are pitch, not clicks.)
        for (edge in listOf(0.3, 0.5, 0.6, 0.8, 1.0, 1.2, 1.5, 1.6, 1.8)) {
            val from = ((edge - 0.003) * sr).toInt()
            val to = ((edge + 0.003) * sr).toInt()
            val worst = (from until to).maxOf { abs(out[it] - out[it - 1]) }
            assertTrue("jump $worst at $edge s vs tone $natural", worst < 2.5f * natural)
        }
    }

    @Test
    fun scratchIsSmoothAndKeepsTheBeat() {
        val input = sine(220.0, 2.0, amp = 0.5)
        val out = run(plan(listOf(FxEvent(FxType.SCRATCH, 0.4, 1.6, p1 = 0.5f, p2 = 1f)), beatSec = 0.4), input)
        val natural = (1 until input.size).maxOf { abs(input[it] - input[it - 1]) }
        // Gentle strokes: never more than about twice the tone's own speed, anywhere in it.
        val worst = ((0.4 * sr).toInt() until (1.7 * sr).toInt()).maxOf { abs(out[it] - out[it - 1]) }
        assertTrue("step $worst vs tone $natural", worst < 2.6f * natural)
        // The track never drops out under the scratch.
        for (from in listOf(0.7, 1.0, 1.3)) assertTrue(rms(out, from, from + 0.2) > 0.25)
    }

    @Test
    fun soundEffectsDuckUnderTheKick() {
        val out = run(plan(listOf(FxEvent(FxType.WHOOSH, 0.0, 2.0, 1f, 1f)), beatSec = 0.5), FloatArray(2 * sr))
        // Right on the beat (1.0 s) the effect dips; mid-beat it is back up.
        assertTrue(rms(out, 1.0, 1.03) < 0.8 * rms(out, 1.2, 1.23))
    }

    @Test
    fun soundEffectsAreLoudEnoughToHear() {
        for (type in listOf(FxType.SIREN, FxType.HORN, FxType.LASER, FxType.IMPACT)) {
            val out = run(plan(listOf(FxEvent(type, 0.0, 1.0, 1f, 1f))), FloatArray(sr))
            assertTrue("$type too quiet", rms(out, 0.05, 0.5) > 0.12)
        }
    }

    @Test
    fun brakeWindsThePlatterDown() {
        val input = sine(1000.0, 2.0)
        val out = run(plan(listOf(FxEvent(FxType.BRAKE, 1.0, 1.5))), input)
        fun crossings(x: FloatArray, from: Double, to: Double) =
            ((from * sr).toInt() until (to * sr).toInt()).count { (x[it] >= 0) != (x[it + 1] >= 0) }
        // Pitch falls as it slows: far fewer zero crossings near the end than at the start.
        assertTrue(crossings(out, 1.40, 1.45) < 0.5 * crossings(out, 1.0, 1.05))
        // And it's live again afterwards.
        assertTrue(rms(out, 1.6, 1.9) > 0.3)
    }

    @Test
    fun exponentialCutoffMapping() {
        assertEquals(20.0, FxPlan.highPassHz(0f), 1e-9)
        assertEquals(200.0, FxPlan.highPassHz(0.5f), 1e-6)
        assertEquals(2000.0, FxPlan.highPassHz(1f), 1e-6)
    }

    @Test
    fun muteLeavesAGapWithoutClicks() {
        val input = sine(220.0, 1.5, amp = 0.5)
        val out = run(plan(listOf(FxEvent(FxType.MUTE, 0.5, 0.75))), input)
        assertTrue(rms(out, 0.51, 0.74) < 0.005)
        assertTrue(rms(out, 0.8, 1.0) > 0.25)
        val natural = (1 until input.size).maxOf { abs(input[it] - input[it - 1]) }
        for (edge in listOf(0.5, 0.75)) {
            val worst = (((edge - 0.005) * sr).toInt() until ((edge + 0.005) * sr).toInt()).maxOf { abs(out[it] - out[it - 1]) }
            assertTrue("jump $worst at $edge s", worst < 2.5f * natural)
        }
    }

    @Test
    fun regionalDrumsPlayOnTheBeat() {
        // Dappu "teen maar": accents on beats one, two and three (0, 0.5, 1.0 s at 120 BPM), not on four.
        val hits = run(plan(listOf(FxEvent(FxType.DAPPU, 0.0, 2.0, p1 = Drum.HIT))), FloatArray(sr * 2))
        val accent = rms(hits, 1.005, 1.06)
        for (beat in listOf(0.0, 0.5, 1.0)) assertTrue("beat at $beat", rms(hits, beat + 0.005, beat + 0.06) > 0.05)
        assertTrue(rms(hits, 1.6, 1.9) < 0.3 * accent)
        // A fill gets busier and louder into the bar line.
        val fill = run(plan(listOf(FxEvent(FxType.DAPPU, 0.0, 2.0, 0.6f, 1f, p1 = Drum.FILL))), FloatArray(sr * 2))
        assertTrue(rms(fill, 1.5, 2.0) > rms(fill, 0.0, 0.5))
    }

    @Test
    fun soundEffectsSampledJustOutsideTheirTimeAreSilentNotACrash() {
        // Effects are picked per block, so the first samples of a block can come before an effect starts.
        val synth = SfxSynth(44100)
        val types = FxType.entries.filter { it.isSfx }
        for (type in types) {
            val e = FxEvent(type, 10.0, 12.0, p1 = if (type == FxType.NADASWARAM) 330f else 1f)
            assertEquals("$type", 0f, synth.sample(listOf(e), 9.999, 0.5), 0f)
            assertEquals("$type", 0f, synth.sample(listOf(e), 12.0, 0.5), 0f)
        }
    }

    @Test
    fun aDeckPlaysThroughABlockThatStartsJustBeforeTheReed() {
        val sr = 44100
        val chain = FxChain(sr, 2)
        chain.plan = FxPlan(0.5, 0.0, Lane(emptyList(), 1f), Lane(emptyList(), 1f), listOf(FxEvent(FxType.NADASWARAM, 1.0003, 3.0, p1 = 330f)))
        chain.reset(0.99)
        val buf = FloatArray(2 * sr) { 0.1f * sin(it * 0.05).toFloat() }
        chain.process(buf, sr)
        assertTrue(buf.all { it.isFinite() })
    }
}
