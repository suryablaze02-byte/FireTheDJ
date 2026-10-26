package com.autodj.dj

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.autodj.dj.fx.FxPlan
import com.autodj.model.TrackInfo
import com.autodj.remote.StreamStore
import com.autodj.remote.YouTubeMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * One turntable: an ExoPlayer with tempo control (pitch preserved) and an effects chain.
 * Volume, EQ and effects all come from the deck's [FxPlan]; the player's own volume stays at 1.
 */
@UnstableApi
class Deck(context: Context) {
    val fx = FxProcessor()
    private val streams = StreamStore.get(context)

    private val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(fx)).build()
    }

    // Index seeking makes seeks in VBR MP3s land where the analysis says they should.
    private val extractors = DefaultExtractorsFactory()
        .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)

    val player: ExoPlayer = ExoPlayer.Builder(context, renderers)
        // YouTube watch links are resolved to their audio as the deck opens them.
        .setMediaSourceFactory(DefaultMediaSourceFactory(YouTubeMedia.dataSourceFactory(context), extractors))
        // Music on the media stream; audio focus is held by the engine for both decks (MixFocus).
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        // Keeps the CPU awake while this deck plays with the screen off.
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    var track: TrackInfo? = null
        private set
    var speed = 1.0
        private set

    /** Key shift in semitones, independent of tempo. */
    var pitchSemis = 0
        private set
    var plan: FxPlan
        get() = fx.plan
        set(value) {
            fx.plan = value
        }

    val positionSec: Double get() = player.currentPosition / 1000.0

    private var retiring: Job? = null

    /** Fades the deck out over [FADE_MS] before stopping it, so a stop is never heard as a cut. */
    fun fadeOutThenUnload(scope: CoroutineScope) {
        retiring?.cancel()
        retiring = scope.launch {
            for (i in 1..FADE_STEPS) {
                player.volume = 1f - i.toFloat() / FADE_STEPS
                delay(FADE_MS / FADE_STEPS)
            }
            unload()
        }
    }

    fun load(t: TrackInfo, startSec: Double, speed: Double, plan: FxPlan, pitchSemis: Int = 0) {
        retiring?.cancel()
        retiring = null
        player.volume = 1f
        track = t
        fx.seekTo(startSec)
        fx.plan = plan
        // A streamed track plays from its downloaded copy when there is one.
        val source = streams.fileFor(t.uri)?.let { Uri.fromFile(it) } ?: Uri.parse(t.uri)
        player.setMediaItem(MediaItem.fromUri(source), (startSec * 1000).toLong())
        player.prepare()
        this.pitchSemis = pitchSemis
        this.speed = speed
        applyParameters()
    }

    fun setSpeed(s: Double) {
        if (s == speed && player.playbackParameters.speed == s.toFloat()) return
        speed = s
        applyParameters()
    }

    private fun applyParameters() {
        player.playbackParameters = PlaybackParameters(speed.toFloat(), 2.0.pow(pitchSemis / 12.0).toFloat())
    }

    fun unload() {
        player.stop()
        player.volume = 1f
        player.clearMediaItems()
        track = null
        fx.plan = FxPlan.EMPTY
        pitchSemis = 0
        speed = 1.0
        applyParameters()
    }

    fun release() = player.release()

    private companion object {
        const val FADE_MS = 250L
        const val FADE_STEPS = 10
    }
}
