package com.autodj.dj

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.autodj.analysis.Camelot
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Law
import com.autodj.dj.fx.Ramp
import com.autodj.model.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class DeckState(
    val uri: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Double,
    val positionSec: Double,
    val durationSec: Double,
    val gain: Float,
    /** The platter is being spun backwards (backspin). */
    val reversing: Boolean,
    /** Camelot code of the key as heard (including any key shift), e.g. "8A". */
    val key: String,
    val pitchSemis: Int,
)

/** An effect sounding on a deck right now: [amount] as automated, [progress] 0..1 through it. */
class ActiveFx(val type: FxType, val amount: Float, val progress: Float, val p1: Float)

/** One deck for the visualizer: see [MixEngine.visual]. */
class DeckVisual(
    /** [SpectrumTap.BANDS] levels, 0..1, low to high. */
    val bands: FloatArray,
    /** Output RMS. */
    val level: Float,
    /** Crossfade gain, 0..1. */
    val gain: Float,
    /** Beats since the track's first downbeat (fractional). */
    val beat: Float,
    /** 0..7: which bar of the 8-bar phrase. */
    val barInPhrase: Int,
    val fx: List<ActiveFx>,
    /** The deck being mixed in (or the hook replay), not the one carrying the set. */
    val incoming: Boolean,
)

class VisualFrame(val decks: List<DeckVisual>, val paused: Boolean)

/** One card in the effects rack. */
data class FxStatus(val name: String, val active: Boolean, val detail: String)

/** One segment of the transition timeline, as fractions of the whole transition. */
data class PhaseUi(val label: String, val start: Float, val end: Float)

data class EngineState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val crate: String? = null,
    val mode: MixMode = MixMode.SMOOTH,
    val index: Int = 0,
    val set: List<TrackInfo> = emptyList(),
    val current: DeckState? = null,
    val incoming: DeckState? = null,
    val next: TrackInfo? = null,
    val recipe: String? = null,
    val transition: String? = null,
    /** Seconds until the transition's first effect. */
    val secondsToMix: Double? = null,
    val mixing: Boolean = false,
    val mixProgress: Float = 0f,
    val mixBar: Int = 0,
    val mixBars: Int = 0,
    /** A track time where B comes in, for the energy map marker. */
    val exitSec: Double? = null,
    val rate: Double = 1.0,
    val pitchSemitones: Double = 0.0,
    val phaseErrMs: Double = 0.0,
    val fx: List<FxStatus> = emptyList(),
    val phases: List<PhaseUi> = emptyList(),
    /** The set's master tempo. */
    val masterBpm: Double = 0.0,
    /** Trick playing right now inside the track, if any. */
    val trickNow: String? = null,
    /** Next trick inside the track and seconds until it. */
    val trickNext: String? = null,
    val secondsToTrick: Double? = null,
    /** Key shift B will be played with in the coming transition. */
    val nextPitchSemis: Int = 0,
    /** How the coming transition handles tempo, e.g. "+1.3%" or "128 → 113 BPM". */
    val tempoChange: String? = null,
)

/**
 * Plays a crate (or every genre) through two decks with no user control over the mixing.
 *
 * When a track starts, [Performer] plans what happens inside it (glide to the master tempo,
 * tempo push, loop-roll builds, hook replay). A hook replay loads the same track on the spare
 * deck at the hook and crossfades onto it on the beat. Once that is done the engine picks the
 * next track, [TransitionPlanner] decides the transition, and both decks get their automation.
 * A control loop starts decks on the planned bar, keeps beats locked by nudging speed, and
 * swaps decks when tails have rung out. The master tempo climbs a little with each track.
 */
@UnstableApi
class MixEngine(context: Context, private val scope: CoroutineScope) {
    private enum class ReplayState { PENDING, LOADED, PLAYING, DONE }

    private val decks = arrayOf(Deck(context), Deck(context))
    private var main = 0
    private val played = mutableListOf<TrackInfo>()
    private val remaining = mutableListOf<TrackInfo>()
    private var setSize = 0
    private var next: TrackInfo? = null
    /** Played + now + the order the rest would play in; recomputed only when the queue moves. */
    private var queue: List<TrackInfo> = emptyList()
    private var crate: String? = null
    private var mode = MixMode.SMOOTH
    private var mainEntryBar = 0
    private var perf: Performance? = null
    private var replayState = ReplayState.DONE
    private var masterBpm = 0.0
    private var plan: Transition? = null
    private val history = mutableListOf<Recipe>()
    private var loaded = false
    private var started = false
    private var paused = false
    private var baseSpeedB = 1.0
    private var phaseErrSec = 0.0
    private var lastAdjust = 0L
    private var lastSpeedSet = 0L
    private var ticks = 0
    private var lastLoggedBeat = -1
    private var loop: Job? = null
    /** Paused because another app took the audio for a moment; plays again when it's back. */
    private var resumeOnFocus = false

    private val focus = MixFocus(context, object : MixFocus.Listener {
        override fun onFocusLost(transient: Boolean) {
            if (loop == null || paused) return
            setPaused(true)
            resumeOnFocus = transient
        }

        override fun onFocusRegained() {
            if (loop != null && paused && resumeOnFocus) setPaused(false)
            resumeOnFocus = false
        }
    })

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    /** Live position of the playing track, for the media session (state is only published per tick). */
    val positionSec: Double get() = decks[main].positionSec

    /**
     * What the visualizer draws this frame: each playing deck's spectrum at the moment being
     * heard, its crossfade gain, where it is in the beat and phrase, and every effect active
     * on it right now. Main thread (it reads the players); cheap enough for every frame.
     */
    fun visual(): VisualFrame? {
        if (_state.value.running.not()) return null
        val bPlaying = started || replayState == ReplayState.PLAYING
        val out = ArrayList<DeckVisual>(2)
        decks[main].visual(incoming = false)?.let { out += it }
        if (bPlaying) decks[1 - main].visual(incoming = true)?.let { out += it }
        return if (out.isEmpty()) null else VisualFrame(out, paused)
    }

    private fun Deck.visual(incoming: Boolean): DeckVisual? {
        val t = track ?: return null
        val pos = positionSec
        val bands = FloatArray(SpectrumTap.BANDS)
        val level = fx.spectrum.frameAt(pos, bands)
        val beats = (pos - t.downbeatSec) / t.beatSec
        val bar = floor(beats / 4).toInt()
        val active = plan.events.filter { it.isActive(pos) }.map {
            ActiveFx(it.type, it.amount(pos), ((pos - it.start) / (it.end - it.start)).toFloat().coerceIn(0f, 1f), it.p1)
        }
        return DeckVisual(
            bands = bands,
            level = if (level < 0) 0f else level,
            gain = plan.gain.at(pos),
            beat = beats.toFloat(),
            barInPhrase = Math.floorMod(bar - t.phraseOffsetBars, 8),
            fx = active,
            incoming = incoming,
        )
    }

    /** Plays [tracks] in an order the engine decides as it goes. [crate] is a label for the UI. */
    fun start(tracks: List<TrackInfo>, crate: String, mode: MixMode = MixMode.SMOOTH) {
        stop()
        if (tracks.isEmpty()) return
        this.crate = crate
        this.mode = mode
        setSize = tracks.size
        val opener = SetPlanner.opener(tracks)
        remaining.addAll(tracks - opener)
        main = 0
        mainEntryBar = 0
        masterBpm = opener.bpm
        decks[0].load(opener, 0.0, 1.0, FxPlan.EMPTY)
        beginTrack(decks[0], opener, 0)
        refreshQueue(opener)
        // During a call the set is ready but waits, paused, instead of playing over it.
        if (focus.request()) decks[0].player.play() else paused = true
        loop = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    fun togglePause() {
        if (loop == null) return
        // Resuming takes the audio back, e.g. after another app had it.
        if (paused && !focus.request()) return
        resumeOnFocus = false
        setPaused(!paused)
    }

    private fun setPaused(p: Boolean) {
        if (p == paused) return
        paused = p
        val a = decks[main]
        val b = decks[1 - main]
        if (paused) {
            a.player.pause()
            b.player.pause()
        } else {
            a.player.play()
            if (started || replayState == ReplayState.PLAYING) b.player.play()
        }
        publish()
    }

    fun stop() {
        loop?.cancel()
        loop = null
        decks.forEach { it.unload() }
        plan = null
        perf = null
        replayState = ReplayState.DONE
        history.clear()
        played.clear()
        remaining.clear()
        next = null
        queue = emptyList()
        loaded = false
        started = false
        paused = false
        resumeOnFocus = false
        focus.abandon()
        _state.value = EngineState()
    }

    fun release() {
        stop()
        decks.forEach { it.release() }
    }

    /** Plans the tricks inside a track that has just become the main deck. */
    private fun beginTrack(deck: Deck, t: TrackInfo, entryBar: Int) {
        // A hook replay is a treat: never on two tracks in a row.
        val p = Performer.plan(t, entryBar, deck.speed, masterBpm, allowReplay = perf?.replay == null, mode = mode)
        perf = p
        Log.i(TAG, "track \"${t.title}\" from bar $entryBar · tricks: ${p.tricks.joinToString { "${it.label} @${formatSec(it.startSec)}" }.ifEmpty { "none" }}")
        replayState = if (p.replay != null) ReplayState.PENDING else ReplayState.DONE
        deck.plan = performancePlan(t, p)
    }

    private fun performancePlan(t: TrackInfo, p: Performance, gain: List<Ramp> = emptyList()) =
        FxPlan(t.beatSec, t.downbeatSec, Lane(gain, 1f), Lane(emptyList(), 1f), p.events)

    /** The track's own tricks, up to a bar before the transition takes over, plus the transition. */
    private fun mergedPlan(t: TrackInfo, p: Performance?, tr: Transition) = FxPlan(
        t.beatSec, t.downbeatSec, tr.aPlan.gain, tr.aPlan.bass,
        p?.events.orEmpty().filter { it.end <= tr.startSec - t.barSec } + tr.aPlan.events,
    )

    private fun tick() {
        val a = decks[main]
        val b = decks[1 - main]
        val ta = a.track ?: return
        if (paused) return publish()

        val posA = a.positionSec
        val aEnded = a.player.playbackState == Player.STATE_ENDED

        // 0. Decide what plays next as soon as a track starts, so the listener can see it; the
        //    transition itself is planned once the track's own tricks (a hook replay) are done.
        if (next == null && remaining.isNotEmpty()) {
            next = SetPlanner.pickNext(ta, remaining, Random.Default)
            refreshQueue(ta)
        }

        // 1. Hook replay, before any transition is planned for this track.
        if (replayState != ReplayState.DONE && plan == null) {
            val before = replayState
            runReplay(a, b, ta, posA)
            // Still replaying, or the decks just swapped (a/b above are stale): wait for the next tick.
            if (replayState != ReplayState.DONE || before == ReplayState.PLAYING) {
                followPerformanceSpeed(a, posA)
                if (++ticks % PUBLISH_EVERY == 0) publish()
                return
            }
        }

        // 2. As soon as the track's own tricks allow, decide what follows it and how.
        if (plan == null && remaining.isNotEmpty()) {
            val chosen = next!!
            plan = TransitionPlanner.plan(ta, mainEntryBar, posA, chosen, history, aPitchSemis = a.pitchSemis, mode = mode, random = Random.Default)
                .also {
                    a.plan = mergedPlan(ta, perf, it)
                    Log.i(
                        TAG,
                        "plan \"${ta.title}\" → \"${chosen.title}\": ${it.description} · effects ${formatSec(it.startSec)}, " +
                            "B in ${formatSec(it.exitSec)} (B from ${formatSec(it.entrySec)}), done ${formatSec(it.endSec)}",
                    )
                }
            refreshQueue(ta)
        }
        val p = plan
        val next = next
        if (p != null && next != null) {
            if (!loaded && posA >= min(p.startSec, p.exitSec) - PRELOAD_SEC) {
                b.load(next, p.entrySec, p.speedForB(ta, next, a.speed), p.bPlan, p.bPitchSemis)
                loaded = true
            }
            p.aSpeedRamp?.let { ramp ->
                if (posA >= ramp.start && !started) {
                    val s = ramp.value(posA).toDouble()
                    if (abs(s - a.speed) > 0.002 || (posA >= ramp.end && s != a.speed)) a.setSpeed(s)
                }
            }
            if (loaded && !started && (posA >= p.exitSec || aEnded)) {
                baseSpeedB = p.speedForB(ta, next, a.speed)
                b.setSpeed(baseSpeedB)
                b.player.play()
                started = true
            }
            if (started) {
                if (p.beatmatched) lockPhase(a, b, p.tempoMultiple)
                if (posA >= p.endSec || aEnded) {
                    finishTransition(p)
                    return publish()
                }
            }
            if (posA < p.startSec) followPerformanceSpeed(a, posA) else logCrossfade(a, b, p, posA)
        } else if (aEnded) {
            stop()
            return
        } else {
            followPerformanceSpeed(a, posA)
        }
        if (++ticks % PUBLISH_EVERY == 0) publish()
    }

    /** Loads the hook on the spare deck, starts it on the jump bar, crossfades, then swaps decks. */
    private fun runReplay(a: Deck, b: Deck, t: TrackInfo, posA: Double) {
        val p = perf ?: return
        val r = p.replay ?: return
        val at = t.barTime(r.atBar)
        val to = t.barTime(r.toBar)
        when (replayState) {
            ReplayState.PENDING -> if (posA >= at - REPLAY_PRELOAD_SEC) {
                if (posA > at - 0.5) {
                    replayState = ReplayState.DONE // too late to set up cleanly; skip it
                } else {
                    // Equal-power crossfade over two beats: both decks play the same track in sync.
                    val fade = 2 * t.beatSec
                    a.plan = performancePlan(t, p, gain = listOf(Ramp(at, at + fade, 1f, 0f, Law.CONSTANT_POWER)))
                    b.load(t, to, a.speed, performancePlan(t, p, gain = listOf(Ramp(to, to + fade, 0f, 1f, Law.CONSTANT_POWER))), a.pitchSemis)
                    replayState = ReplayState.LOADED
                }
            }
            ReplayState.LOADED -> if (posA >= at) {
                baseSpeedB = a.speed
                b.setSpeed(baseSpeedB)
                b.player.play()
                replayState = ReplayState.PLAYING
                Log.i(TAG, "hook replay in \"${t.title}\": ${formatSec(at)} → back to ${formatSec(to)}")
            }
            ReplayState.PLAYING -> {
                lockPhase(a, b, 1.0)
                if (posA >= at + t.barSec) {
                    a.fadeOutThenUnload(scope)
                    main = 1 - main
                    // The hook played twice: count those bars as played for pacing the exit.
                    mainEntryBar -= r.atBar - r.toBar
                    decks[main].plan = performancePlan(t, p)
                    phaseErrSec = 0.0
                    replayState = ReplayState.DONE
                }
            }
            ReplayState.DONE -> Unit
        }
    }

    /** Applies the performance's speed lane (master-tempo glide, tempo push), rate-limited. */
    private fun followPerformanceSpeed(deck: Deck, pos: Double) {
        val p = perf ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSpeedSet < ADJUST_MS) return
        val s = p.speed.at(pos).toDouble()
        if (abs(s - deck.speed) > 0.0015) {
            deck.setSpeed(s)
            lastSpeedSet = now
        }
    }

    /**
     * Compares where each deck is within its beat and nudges B's speed to close the gap.
     * Players start with some latency, so this is what actually lines the kicks up.
     */
    private fun lockPhase(a: Deck, b: Deck, multiple: Double) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAdjust < ADJUST_MS) return
        lastAdjust = now
        val ta = a.track ?: return
        val tb = b.track ?: return
        val unitA = max(1.0, 1 / multiple)
        val unitB = max(1.0, multiple)
        val phaseA = frac((a.positionSec - ta.downbeatSec) / ta.beatSec / unitA)
        val phaseB = frac((b.positionSec - tb.downbeatSec) / tb.beatSec / unitB)
        var diff = phaseB - phaseA
        if (diff >= 0.5) diff -= 1 else if (diff < -0.5) diff += 1
        val errSec = diff * unitA * ta.beatSec / a.speed
        phaseErrSec = errSec
        // Coarse steps: every speed change makes ExoPlayer drain and restart its time stretcher,
        // so the speed only changes when the correction needed moves by a whole step.
        val raw = if (abs(errSec) < 0.008) 0.0 else (-errSec / 1.5).coerceIn(-0.05, 0.05)
        val correction = (raw / LOCK_STEP).roundToInt() * LOCK_STEP
        b.setSpeed(baseSpeedB * (1 + correction))
    }

    private fun formatSec(sec: Double): String {
        val s = max(0, sec.roundToInt())
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** One log line per beat of a transition: both decks' volume, so the crossfade can be checked on the phone. */
    private fun logCrossfade(a: Deck, b: Deck, p: Transition, posA: Double) {
        val ta = a.track ?: return
        val beatNo = floor((posA - p.startSec) / ta.beatSec).toInt()
        if (beatNo == lastLoggedBeat || posA > p.handoverSec + ta.barSec) return
        lastLoggedBeat = beatNo
        val gainA = a.plan.gain.at(posA)
        val gainB = if (started) b.plan.gain.at(b.positionSec) else 0f
        Log.i(TAG, "xfade ${p.recipe.label} beat $beatNo: A ${(gainA * 100).roundToInt()}% · B ${(gainB * 100).roundToInt()}%")
    }

    private fun finishTransition(p: Transition) {
        lastLoggedBeat = -1
        Log.i(TAG, "done ${p.recipe.label}: \"${decks[main].track?.title}\" → \"${decks[1 - main].track?.title}\"")
        decks[main].track?.let { played += it }
        decks[main].fadeOutThenUnload(scope)
        main = 1 - main
        next?.let { remaining.remove(it) }
        next = null
        history += p.recipe
        plan = null
        loaded = false
        started = false
        phaseErrSec = 0.0
        val deck = decks[main]
        deck.setSpeed(baseSpeedB)
        val t = deck.track ?: return
        // Tempo arc: keep climbing through most of the set, then hold.
        val heard = t.bpm * deck.speed
        masterBpm = heard + if (played.size < setSize * ARC_SHARE) arcStep(mode) else 0.0
        mainEntryBar = p.entryBar
        beginTrack(deck, t, p.entryBar)
        refreshQueue(t)
    }

    private fun refreshQueue(current: TrackInfo) {
        val n = next
        val ahead = if (n != null) listOf(n) + SetPlanner.upcoming(n, remaining - n) else SetPlanner.upcoming(current, remaining)
        queue = played + current + ahead
    }

    private fun publish() {
        val a = decks[main]
        val b = decks[1 - main]
        val ta = a.track ?: return
        val p = plan
        val next = next
        val posA = a.positionSec
        val bPlaying = started || replayState == ReplayState.PLAYING
        val posB = if (bPlaying) b.positionSec else null
        val bPlan = if (started) b.plan else null
        // "Mixing" runs until B has fully taken over; A may keep ringing (echo tails) after that.
        val span = p?.let { it.handoverSec - it.startSec } ?: 1.0
        val mixing = p != null && posA >= p.startSec && posA < p.handoverSec
        val mixBars = p?.let { (span / ta.barSec).roundToInt() } ?: 0
        val rate = when {
            started -> b.speed
            p != null && next != null -> p.speedForB(ta, next, a.speed)
            else -> a.speed
        }
        val tricks = perf?.tricks.orEmpty()
        val now = tricks.firstOrNull { posA >= it.startSec && posA < it.endSec }
            ?: if (replayState == ReplayState.PLAYING) tricks.firstOrNull { it.label == "Hook replay" } else null
        val upcoming = tricks.filter { it.startSec > posA && (p == null || it.endSec <= p.startSec) }.minByOrNull { it.startSec }
        _state.value = EngineState(
            running = true,
            paused = paused,
            crate = crate,
            mode = mode,
            index = played.size,
            set = queue,
            current = a.toState(posA, a.plan),
            incoming = if (started && posB != null) b.toState(posB, b.plan) else null,
            next = next,
            recipe = p?.recipe?.label,
            transition = p?.description,
            secondsToMix = if (p != null && posA < p.startSec) (p.startSec - posA) / a.speed else null,
            mixing = mixing,
            mixProgress = if (p != null) ((posA - p.startSec) / span).coerceIn(0.0, 1.0).toFloat() else 0f,
            mixBar = if (mixing) min(mixBars, floor((posA - p!!.startSec) / ta.barSec).toInt() + 1) else 0,
            mixBars = mixBars,
            exitSec = p?.exitSec,
            rate = rate,
            pitchSemitones = 12 * log2(rate),
            phaseErrMs = phaseErrSec * 1000,
            fx = fxRack(a.plan, posA, bPlan, posB),
            phases = p?.phases?.map {
                PhaseUi(it.label, ((it.startSec - p.startSec) / span).toFloat(), ((it.endSec - p.startSec) / span).toFloat())
            }.orEmpty(),
            masterBpm = masterBpm,
            trickNow = now?.label,
            trickNext = upcoming?.label,
            secondsToTrick = upcoming?.let { (it.startSec - posA) / a.speed },
            nextPitchSemis = p?.bPitchSemis ?: 0,
            tempoChange = if (p != null && next != null) tempoChange(p, ta, a.speed, next) else null,
        )
    }

    /**
     * Beat-matched: how much B is stretched ("+1.3%"). Otherwise the two tempos as heard, since
     * B then plays at its own speed ("128 → 113 BPM"), ramped into for a Loop & ramp.
     */
    private fun tempoChange(p: Transition, a: TrackInfo, aSpeed: Double, b: TrackInfo): String {
        if (p.beatmatched) {
            val pct = (p.speedForB(a, b, aSpeed) - 1) * 100
            return if (abs(pct) < 0.05) "same tempo" else "${if (pct > 0) "+" else ""}${"%.1f".format(pct)}%"
        }
        val from = (a.bpm * aSpeed).roundToInt()
        val to = b.bpm.roundToInt()
        return if (p.recipe == Recipe.LOOP_RAMP) "ramp $from → $to BPM" else "$from → $to BPM"
    }

    private fun Deck.toState(pos: Double, plan: FxPlan): DeckState? {
        val t = track ?: return null
        return DeckState(
            uri = t.uri,
            title = t.title,
            artist = t.artist,
            genre = t.genre,
            bpm = t.bpm * speed,
            positionSec = pos,
            durationSec = t.durationSec,
            gain = plan.gain.at(pos),
            reversing = plan.active(FxType.BACKSPIN, pos) != null,
            key = Camelot.code(t.keyPc + pitchSemis, t.keyMinor),
            pitchSemis = pitchSemis,
        )
    }

    private fun frac(x: Double) = x - floor(x)

    private companion object {
        const val TAG = "AutoDJ"
        const val TICK_MS = 15L
        const val ADJUST_MS = 150L
        const val LOCK_STEP = 0.005
        const val PRELOAD_SEC = 8.0
        const val REPLAY_PRELOAD_SEC = 6.0
        const val PUBLISH_EVERY = 3

        /** Master tempo rises this much per track through the first [ARC_SHARE] of the set. */
        /** Smooth leans on tempo for energy; Trickster on tricks. */
        fun arcStep(mode: MixMode) = if (mode == MixMode.SMOOTH) 1.0 else 0.6
        const val ARC_SHARE = 0.7

        /** Rack cards in display order, each covering one or more effect types. */
        val RACK = listOf(
            "Filter" to listOf(FxType.HIGH_PASS, FxType.LOW_PASS),
            "Echo" to listOf(FxType.ECHO),
            "Reverb" to listOf(FxType.REVERB),
            "Loop roll" to listOf(FxType.LOOP_ROLL),
            "Scratch" to listOf(FxType.SCRATCH),
            "Spin / brake" to listOf(FxType.BACKSPIN, FxType.BRAKE),
            "Gate" to listOf(FxType.GATE),
            "Tremolo" to listOf(FxType.TREMOLO),
            "Flanger" to listOf(FxType.FLANGER),
            "Phaser" to listOf(FxType.PHASER),
            "Lo-fi" to listOf(FxType.LOFI),
            "Vocals" to listOf(FxType.VOCAL_CUT, FxType.VOCAL_ISOLATE),
            "Sound FX" to FxType.entries.filter { it.isSfx },
        )

        fun fxRack(aPlan: FxPlan, posA: Double, bPlan: FxPlan?, posB: Double?): List<FxStatus> {
            val cards = RACK.map { (name, types) ->
                val onA = types.firstNotNullOfOrNull { aPlan.active(it, posA) }
                val onB = if (bPlan != null && posB != null) types.firstNotNullOfOrNull { bPlan.active(it, posB) } else null
                val uses = types.any { aPlan.uses(it) || bPlan?.uses(it) == true }
                when {
                    onA != null -> FxStatus(name, true, describe(onA, "A", posA))
                    onB != null -> FxStatus(name, true, describe(onB, "B", posB!!))
                    else -> FxStatus(name, false, if (uses) "Planned" else "Standby")
                }
            }
            val swapping = aPlan.bass.movingAt(posA) || (bPlan != null && posB != null && bPlan.bass.movingAt(posB))
            val swapUsed = aPlan.bass.hasRamps || bPlan?.bass?.hasRamps == true
            return cards + FxStatus("Bass EQ", swapping, if (swapping) "Low-shelf swap" else if (swapUsed) "Planned" else "Standby")
        }

        private fun describe(e: FxEvent, deck: String, t: Double): String {
            val amount = e.amount(t)
            return when (e.type) {
                FxType.HIGH_PASS -> "$deck high-pass ${hz(FxPlan.highPassHz(amount))}"
                FxType.LOW_PASS -> "$deck low-pass ${hz(FxPlan.lowPassHz(amount))}"
                FxType.ECHO -> "$deck ${beats(e.p1)} beat · g ${"%.2f".format(e.p2)}"
                FxType.REVERB -> "$deck RT60 ${"%.1f".format(e.p1)} s"
                FxType.LOOP_ROLL -> "$deck ${beats(e.p1)} beat roll"
                FxType.SCRATCH -> "$deck baby scratch"
                FxType.BACKSPIN -> "$deck spin-back"
                FxType.GATE -> "$deck ${note(e.p1)} square"
                FxType.TREMOLO -> "$deck sine AM ${note(e.p1)}"
                FxType.FLANGER -> "$deck ${e.p1} Hz sweep"
                FxType.PHASER -> "$deck 4-stage ${e.p1} Hz"
                FxType.LOFI -> {
                    val bits = (16 - amount * (16 - e.p1)).roundToInt()
                    val factor = 1 + (amount * (e.p2 - 1)).roundToInt()
                    "$deck $bits-bit ÷$factor"
                }
                FxType.VOCAL_CUT -> "$deck vocal out ${(amount * 100).roundToInt()}%"
                FxType.VOCAL_ISOLATE -> "$deck vocal up ${(amount * 100).roundToInt()}%"
                FxType.BRAKE -> "$deck turntable stop"
                else -> if (e.type.isSfx) "$deck ${e.type.label.lowercase()}" else e.type.label
            }
        }

        private fun hz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "${f.roundToInt()} Hz"

        private fun beats(b: Float) = when (b) {
            0.125f -> "1/8"
            0.25f -> "1/4"
            0.5f -> "1/2"
            0.75f -> "3/4"
            else -> if (b == b.roundToInt().toFloat()) "${b.roundToInt()}" else "%.2f".format(b)
        }

        /** A period in beats as a note value: 0.5 beat = 1/8 note. */
        private fun note(b: Float) = "1/${(4 / b).roundToInt()}"
    }
}
