package com.autodj.dj

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Presents the two-deck [MixEngine] to a MediaSession as one ordinary player: the set is the
 * playlist and play/pause pauses the mix. "Next" is the skip: the song mixes out at its next
 * phrase (pressed again: its next bar), the way [MixEngine.skip] does from the app.
 * Setting a crate's media item (Android Auto's browse list) starts a set from it via [onPlayCrate].
 */
@UnstableApi
class EnginePlayer(
    private val engine: MixEngine,
    scope: CoroutineScope,
    private val onPlayCrate: (mediaId: String) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    init {
        // Engine state ticks every few ms; only re-read when something the session shows changes.
        scope.launch {
            engine.state
                .map { listOf(it.running, it.paused, it.index, it.set, it.next != null) }
                .distinctUntilChanged()
                .collect { invalidateState() }
        }
    }

    override fun getState(): State {
        val s = engine.state.value
        // "Next" skips (while a skip can still do something); there is no going back.
        val commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_STOP,
                COMMAND_RELEASE,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_GET_TIMELINE,
                // How a car (or any media browser) starts a set: it sets a crate's item, prepares and plays.
                COMMAND_SET_MEDIA_ITEM,
                COMMAND_CHANGE_MEDIA_ITEMS,
                COMMAND_PREPARE,
            )
            // Kept while there's a next song, so the notification's buttons don't jump about.
            .addIf(COMMAND_SEEK_TO_NEXT, s.running && s.next != null)
            .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, s.running && s.next != null)
            .build()
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(s.running && !s.paused, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        if (!s.running || s.set.isEmpty()) {
            return builder.setPlaybackState(STATE_IDLE).build()
        }
        val playlist = s.set.map { t ->
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist.ifBlank { null })
                .setGenre(t.genre.ifBlank { null })
                .build()
            MediaItemData.Builder(t.uri)
                .setMediaItem(MediaItem.Builder().setMediaId(t.uri).setMediaMetadata(metadata).build())
                .setDurationUs((t.durationSec * 1_000_000).toLong())
                .build()
        }
        return builder
            .setPlaybackState(STATE_READY)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(s.index.coerceIn(0, playlist.size - 1))
            .setContentPositionMs { (engine.positionSec * 1000).toLong() }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val s = engine.state.value
        if (s.running && playWhenReady == s.paused) engine.togglePause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId?.let(onPlayCrate)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (seekCommand == COMMAND_SEEK_TO_NEXT || seekCommand == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) engine.skip()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        engine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
