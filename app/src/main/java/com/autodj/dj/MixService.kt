package com.autodj.dj

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.autodj.lyrics.LyricsStore
import com.autodj.openAppIntent
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground media service: keeps the process alive and shows the playback notification and
 * lock-screen controls while a set is playing. Media3 moves it in and out of the foreground
 * as the session player starts and stops.
 *
 * It is also a media library, which is how Android Auto lists FireTheDJ in the car: the
 * browse tree is the genre crates, and picking one starts a set from it.
 */
@UnstableApi
class MixService : MediaLibraryService() {
    private val scope = MainScope()
    private lateinit var engine: MixEngine
    private lateinit var crates: CrateLibrary
    private var session: MediaLibrarySession? = null

    /** Pause instead of blasting the mix out of the speaker when headphones are unplugged. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val s = engine.state.value
            if (s.running && !s.paused) engine.togglePause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = EngineHolder.get(this)
        LyricsStore.init(this)
        crates = CrateLibrary(this)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, EnginePlayer(engine, scope, ::playCrate), LibraryCallback())
            .setSessionActivity(openApp)
            .build()
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Starts a set from the crate a media browser picked. */
    private fun playCrate(mediaId: String) {
        scope.launch {
            val crate = withContext(Dispatchers.IO) { crates.crate(mediaId) } ?: return@launch
            Log.i("AutoDJ", "set started from a media browser: ${crate.label} (${crate.tracks.size})")
            engine.start(crate.tracks, crate.label, crates.mode())
        }
    }

    /** Root → one playable item per crate. */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            if (parentId != CrateLibrary.ROOT) return@future LibraryResult.ofItemList(ImmutableList.of(), params)
            val items = withContext(Dispatchers.IO) { crates.crates() }.map(::crateItem)
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val crate = withContext(Dispatchers.IO) { crates.crate(mediaId) }
            if (crate != null) LibraryResult.ofItem(crateItem(crate), null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        // Crate items carry no URI; the player starts the set from the id itself.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
    }

    private fun root() = MediaItem.Builder()
        .setMediaId(CrateLibrary.ROOT)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle("FireTheDJ").setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
        )
        .build()

    private fun crateItem(crate: CrateLibrary.Crate) = MediaItem.Builder()
        .setMediaId(crate.mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(crate.label)
                .setSubtitle("AI mix · ${crate.tracks.size} tracks")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .build(),
        )
        .build()

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away from recents ends the set. Not while Android Auto is playing it:
        // the car is then where the set lives, and clearing the phone's recents shouldn't cut it.
        val inCar = session?.connectedControllers?.any { it.packageName == ANDROID_AUTO } == true
        if (inCar) return
        engine.stop()
        stopSelf()
    }

    private companion object {
        const val ANDROID_AUTO = "com.google.android.projection.gearhead"
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
