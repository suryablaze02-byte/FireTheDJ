package com.autodj.dj.stems

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodj.analysis.PcmDecoder
import com.autodj.remote.StreamStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Separates the stretches of songs that stems make better, in the background, one at a time:
 * each mix's outgoing run-out and incoming entry, and (if asked) the bass-kill drops and word
 * loops inside a song. A window takes about as long as the audio it covers once analysis is
 * done, so asking when the mix or the song is planned (a minute or more ahead) is usually in
 * time; if it isn't, that moment simply uses its EQ moves as before.
 *
 * Order: a mix whose window is nearly due goes first; otherwise whatever is needed soonest.
 */
object StemJobs {
    private const val TAG = "AutoDJ"

    /** The deck waits this long into a window for its stems (see FxProcessor's LATE_SEC). */
    private const val LATE_GRACE_SEC = 3.0

    /** Longest stretch separated: about a 16-bar blend with room either side, and bounded memory. */
    const val MAX_SEC = 45.0

    /** Seconds of work per second of audio, roughly, during a set: for judging what still fits. */
    private const val WORK_PER_SEC = 2.0

    /** How long the idle worker waits after the first request for the rest of its burst. */
    private const val GATHER_MS = 300L

    /** A mix with less slack than this before its window is due is done before anything else. */
    private const val MIX_FIRST_SLACK_MS = 60_000L

    /** The model stays loaded this long after the last job: longer than a song, so a set keeps it warm. */
    private const val IDLE_UNLOAD_MS = 5 * 60_000L

    private class Job(
        val key: String, val context: Context, val uri: String, val title: String,
        val from: Double, val to: Double, val deadline: Long, val mix: Boolean, val generation: Int,
    ) {
        /** When it would have to start to be ready in time. */
        val latestStart get() = deadline - ((to - from) * WORK_PER_SEC * 1000).toLong()
    }

    private val lock = Object()
    private val queue = mutableListOf<Job>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    // Normal priority: ONNX Runtime's threads inherit it, and at background priority analysis and
    // playback starved them to half speed. The audio threads run far above it either way.
    private val worker = Thread({ while (true) runCatching { run(take()) } }, "stems").apply { isDaemon = true; start() }

    /** Bumped when a set starts: work asked for by an earlier set is dropped, even halfway through. */
    @Volatile
    private var generation = 0

    /** A new set: nothing queued for the last one is worth finishing. */
    fun newSet() {
        generation++
        synchronized(lock) {
            queue.forEach { pending.remove(it.key) }
            queue.clear()
        }
    }

    private class Stale : RuntimeException()

    /** A window is being separated: analysis starts no new songs meanwhile, so the mix gets its stems in time. */
    @Volatile
    var busy = false
        private set

    /**
     * Separates [fromSec]..[toSec] of [uri]. [neededInSec] is how soon the deck reaches the window:
     * work that can't finish by then (plus the few seconds the deck waits) is dropped, not wasted.
     * [mix] marks a mix's window, which goes ahead of a trick's when it's nearly due.
     */
    fun request(context: Context, uri: String, title: String, fromSec: Double, toSec: Double, neededInSec: Double, mix: Boolean = true) {
        if (!Stems.active(context)) return
        val from = max(0.0, fromSec)
        val to = min(toSec, from + MAX_SEC)
        if (to - from < 4) return
        if (StemWindows.has(uri, from, to)) return
        val key = "$uri@${from.toInt()}"
        if (!pending.add(key)) return
        val deadline = System.currentTimeMillis() + ((neededInSec + LATE_GRACE_SEC) * 1000).toLong()
        Log.i(TAG, "stems: asked for \"$title\" ${sec(from)}–${sec(to)}${if (mix) "" else " (trick)"}, needed in ${neededInSec.toInt()} s")
        synchronized(lock) {
            queue += Job(key, context.applicationContext, uri, title, from, to, deadline, mix, generation)
            lock.notifyAll()
        }
    }

    /** The next job worth doing, waiting for one; drops what can no longer be ready in time. */
    private fun take(): Job = synchronized(lock) {
        // A song's mix and trick windows are asked for together: let the burst arrive before choosing.
        if (queue.isEmpty()) {
            while (queue.isEmpty()) idleWait()
            // Each new request wakes the wait, so wait out the whole gathering time.
            val until = System.currentTimeMillis() + GATHER_MS
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) break
                lock.wait(left)
            }
        }
        while (true) {
            val now = System.currentTimeMillis()
            queue.removeAll { job ->
                val dead = job.generation != generation || job.latestStart < now - (LATE_GRACE_SEC * 1000).toLong()
                if (dead) {
                    pending.remove(job.key)
                    if (job.generation == generation) Log.i(TAG, "stems: skipped \"${job.title}\": not enough time before it's needed")
                }
                dead
            }
            if (queue.isNotEmpty()) {
                val urgentMix = queue.filter { it.mix }.minByOrNull { it.deadline }?.takeIf { it.latestStart - now < MIX_FIRST_SLACK_MS }
                val job = urgentMix ?: queue.minBy { it.deadline }
                queue -= job
                return job
            }
            idleWait()
        }
        error("unreachable")
    }

    /**
     * The loaded model, kept between jobs on the worker thread: loading it costs about 1.5 s and its
     * first few chunks run slow, about 4 s on every window (measured on a Moto G64), for the same stems.
     * Held, it costs about 170–300 MB, so it's let go after [IDLE_UNLOAD_MS] without work.
     */
    private var model: StemSession? = null

    /** Waits for a request, letting the model go if none comes for a while. Called holding [lock]. */
    private fun idleWait() {
        if (model == null) return lock.wait()
        lock.wait(IDLE_UNLOAD_MS)
        if (queue.isEmpty()) unload()
    }

    private fun unload() {
        model?.let { runCatching { it.close() } }
        model = null
    }

    private fun run(job: Job) {
        busy = true
        try {
            separate(job.context, job.uri, job.title, job.from, job.to) {
                job.generation != generation || System.currentTimeMillis() > job.deadline
            }
        } catch (e: Stale) {
            Log.i(TAG, "stems: dropped \"${job.title}\": too late for its moment, or its set ended")
        } catch (e: Throwable) {
            // Out of memory, a decoder that can't seek, a model that won't load: this moment uses EQ.
            Log.w(TAG, "stems: \"${job.title}\" ${sec(job.from)}–${sec(job.to)} not separated: $e")
            unload() // after running out of memory, the next job starts from a fresh model
        } finally {
            busy = false
            pending.remove(job.key)
        }
    }

    private fun separate(context: Context, uri: String, title: String, from: Double, to: Double, stale: () -> Boolean) {
        val engine = Stems.engine ?: return
        val t0 = System.currentTimeMillis()
        val source = StreamStore.get(context).fileFor(uri)?.let { Uri.fromFile(it) } ?: Uri.parse(uri)
        val pcm = PcmDecoder.decodeStereo(context, source, from, to)
        if (pcm.channels < 2) {
            Log.i(TAG, "stems: \"$title\" is mono; its mixes use EQ")
            return
        }
        val rate = pcm.sampleRate
        val frames = pcm.frames
        val tDecode = System.currentTimeMillis() - t0
        val l44 = Resampler.resample(pcm.left, rate, DemucsDsp.SAMPLE_RATE)
        val r44 = Resampler.resample(pcm.right, rate, DemucsDsp.SAMPLE_RATE)
        val t1 = System.currentTimeMillis()
        val session = model ?: engine.open(StemModel.file(context)).also { model = it }
        val stems44 = DemucsDsp.separate(l44, r44, l44.size, session::run) { _, _ -> if (stale()) throw Stale() }
        val tModel = System.currentTimeMillis() - t1
        // Back to the song's rate; "other" is whatever the other three leave, so the four add to the original exactly.
        val stems = Array(DemucsDsp.STEMS) { Array(2) { FloatArray(0) } }
        val restL = pcm.left.copyOf()
        val restR = pcm.right.copyOf()
        for (s in listOf(0, 1, 3)) {
            val l = fit(Resampler.resample(stems44[s][0], DemucsDsp.SAMPLE_RATE, rate), frames)
            val r = fit(Resampler.resample(stems44[s][1], DemucsDsp.SAMPLE_RATE, rate), frames)
            for (i in 0 until frames) { restL[i] -= l[i]; restR[i] -= r[i] }
            stems[s] = arrayOf(l, r)
        }
        stems[2] = arrayOf(restL, restR)
        val guide = FloatArray(frames) { (pcm.left[it] + pcm.right[it]) * 0.5f }
        StemWindows.put(StemWindow.pack(uri, title, pcm.startSec, rate, stems, guide))
        Log.i(
            TAG,
            "stems: \"$title\" ${sec(pcm.startSec)}–${sec(pcm.startSec + frames / rate.toDouble())} separated in " +
                "${(System.currentTimeMillis() - t0) / 1000} s (decode ${tDecode / 1000} s, model ${tModel / 1000} s)",
        )
    }

    /** Exactly [n] frames: resampling there and back can be a frame or two out. */
    private fun fit(x: FloatArray, n: Int) = if (x.size == n) x else x.copyOf(n)

    private fun sec(s: Double) = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)
}
