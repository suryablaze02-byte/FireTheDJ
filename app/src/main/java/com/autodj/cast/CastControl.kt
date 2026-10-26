package com.autodj.cast

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.autodj.dj.EngineHolder
import com.autodj.ui.vis.VisualizerStyle
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** Where a Chromecast cast stands, for the cast button and dialog. */
sealed interface CastStatus {
    data object Off : CastStatus
    /** Connected to [device]; waiting for the listener to allow capturing the mix's audio. */
    data class NeedsCapture(val device: String) : CastStatus
    data class Streaming(val device: String) : CastStatus
    data class Failed(val reason: String) : CastStatus
}

/**
 * Google Cast sessions: when one starts the phone captures and streams the mix ([MixStreamer]
 * in [CastStreamService]) and tells the TV to play it. FireTheDJ's own receiver page also gets
 * the playing track and the visualizer to draw; Google's default receiver just plays the audio.
 *
 * While the TV plays, the phone's media volume is turned down to nothing (and back after), so
 * the two don't play the same mix seconds apart.
 */
@UnstableApi
object CastControl {
    private val scope = MainScope()
    private var appContext: Context? = null
    private var castContext: CastContext? = null
    private var session: CastSession? = null
    private var streamer: MixStreamer? = null
    private var trackJob: Job? = null
    private var pauseJob: Job? = null
    private var savedVolume: Int? = null
    private var artVersion = 0

    private val _status = MutableStateFlow<CastStatus>(CastStatus.Off)
    val status: StateFlow<CastStatus> = _status

    /** Track uri → artwork file, from the library. */
    @Volatile var artworkFor: (String) -> String? = { null }

    /** The visualizer the receiver page draws; follows the one picked in the app. */
    var style: VisualizerStyle = VisualizerStyle.CLUB
        set(value) {
            field = value
            sendToReceiver(JSONObject().put("type", "style").put("style", value.name))
        }

    /** The Cast framework, or null without Google Play services. Main thread. */
    fun init(context: Context): CastContext? {
        castContext?.let { return it }
        appContext = context.applicationContext
        val cast = runCatching { CastContext.getSharedInstance(context.applicationContext) }
            .onFailure { Log.w("AutoDJ", "Google Cast unavailable: ${it.message}") }
            .getOrNull() ?: return null
        cast.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        castContext = cast
        return cast
    }

    /** Ends the cast; the mix carries on on the phone. */
    fun stop() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    /** The listener said no to capturing audio: there is nothing to send the TV. */
    fun onCaptureDenied() {
        _status.value = CastStatus.Failed("Casting needs permission to capture FireTheDJ's audio")
        stop()
    }

    internal fun onStreamReady(s: MixStreamer, url: String) {
        streamer = s
        val cast = session ?: return
        val device = cast.castDevice?.friendlyName.orEmpty()
        appContext?.let { ctx -> EngineHolder.get(ctx).state.value.current?.let { s.artwork = artworkFor(it.uri)?.let(::File) } }
        muteThePhone()
        playOnTv(url)
        _status.value = CastStatus.Streaming(device)
        followTracks()
        followPause(url)
    }

    /** Has the TV play the stream from now: at the start of the cast, and after a pause. */
    private fun playOnTv(url: String) {
        val cast = session ?: return
        if (CastReceiver.custom) {
            sendToReceiver(JSONObject().put("type", "stream").put("url", url).put("style", style.name))
            return
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, "FireTheDJ")
            putString(MediaMetadata.KEY_ARTIST, "AI DJ mix")
            streamer?.artUrl(++artVersion)?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val media = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("audio/aac")
            .setMetadata(metadata)
            .build()
        cast.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true).build())
            ?.setResultCallback { r -> Log.i("AutoDJ", "TV load of the mix stream: ${r.status}") }
    }

    /**
     * The TV plays several seconds of buffered stream behind the phone, so pausing the set alone
     * would leave it playing on. A pause stops the TV's player outright (dropping that buffer);
     * play starts the stream again from the moment it resumes.
     */
    private fun followPause(url: String) {
        pauseJob?.cancel()
        val context = appContext ?: return
        pauseJob = scope.launch {
            EngineHolder.get(context).state.map { it.running && it.paused }.distinctUntilChanged().drop(1).collect { paused ->
                Log.i("AutoDJ", if (paused) "set paused: stopping the TV" else "set playing: restarting the TV stream")
                if (!paused) {
                    playOnTv(url)
                } else if (CastReceiver.custom) {
                    sendToReceiver(JSONObject().put("type", "pause"))
                } else {
                    session?.remoteMediaClient?.stop()
                }
            }
        }
    }

    internal fun onStreamFailed(reason: String) {
        Log.w("AutoDJ", "cast stream failed: $reason")
        _status.value = CastStatus.Failed(reason)
        stop()
    }

    internal fun onStreamStopped() {
        streamer = null
        trackJob?.cancel()
        pauseJob?.cancel()
        restoreThePhone()
        // The capture ended on its own (e.g. stopped from the notification): end the cast too.
        if (session != null) stop()
    }

    /** Keeps the TV's artwork (and the receiver page's track line) on the track playing now. */
    private fun followTracks() {
        trackJob?.cancel()
        val context = appContext ?: return
        trackJob = scope.launch {
            EngineHolder.get(context).state.distinctUntilChangedBy { it.current?.uri }.collect { state ->
                val deck = state.current ?: return@collect
                val s = streamer ?: return@collect
                s.artwork = artworkFor(deck.uri)?.let(::File)
                sendToReceiver(
                    JSONObject().put("type", "track").put("title", deck.title).put("artist", deck.artist)
                        .put("bpm", deck.bpm).put("art", s.artUrl(++artVersion)),
                )
            }
        }
    }

    private fun sendToReceiver(message: JSONObject) {
        if (!CastReceiver.custom) return
        session?.takeIf { it.isConnected }?.sendMessage(CastReceiver.NAMESPACE, message.toString())
    }

    private fun muteThePhone() {
        val audio = appContext?.getSystemService(AudioManager::class.java) ?: return
        if (savedVolume == null) savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    }

    private fun restoreThePhone() {
        val volume = savedVolume ?: return
        savedVolume = null
        appContext?.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = started(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) {
            Log.i("AutoDJ", "cast session resumed")
            started(s)
        }
        override fun onSessionEnded(s: CastSession, error: Int) {
            Log.i("AutoDJ", "cast session ended (status $error)")
            ended()
        }
        override fun onSessionStartFailed(s: CastSession, error: Int) {
            Log.w("AutoDJ", "cast session failed to start (status $error)")
            _status.value = CastStatus.Failed("Couldn't connect to the TV (error $error)")
        }
        override fun onSessionStarting(s: CastSession) = Unit
        override fun onSessionEnding(s: CastSession) = Unit
        override fun onSessionResuming(s: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(s: CastSession, error: Int) = Unit
        override fun onSessionSuspended(s: CastSession, reason: Int) {
            Log.w("AutoDJ", "cast session suspended (reason $reason)")
        }
    }

    private fun started(s: CastSession) {
        session = s
        val device = s.castDevice?.friendlyName.orEmpty()
        Log.i("AutoDJ", "cast session with $device")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onStreamFailed("Casting the mix to a Chromecast needs Android 10 or newer")
            return
        }
        if (streamer == null) _status.value = CastStatus.NeedsCapture(device)
    }

    private fun ended() {
        session = null
        trackJob?.cancel()
        pauseJob?.cancel()
        appContext?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) CastStreamService.stop(it) }
        restoreThePhone()
        if (_status.value !is CastStatus.Failed) _status.value = CastStatus.Off
    }
}
