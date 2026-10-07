package com.fidelis.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fidelis.player.data.model.Track
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject. Inject
import javax.inject.Singleton

data class PlayerUiState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val hasMedia: Boolean = false,
)

/**
 * Connects the UI to the PlaybackService's MediaSession, exposes live state, and relays
 * transport commands. Starting playback makes Media3 post the system notification pill.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var pending: (MediaController.() -> Unit)? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState(player)
    }

    init { ensureConnected() }

    private fun ensureConnected() {
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = f.get()
            controller = c
            c.addListener(listener)
            pushState(c)
            pending?.let { c.it() }
            pending = null
        }, ContextCompat.getMainExecutor(context))
    }

    private fun pushState(p: Player) {
        val item = p.currentMediaItem
        _state.value = PlayerUiState(
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = p.isPlaying,
            durationMs = p.duration.coerceAtLeast(0L),
            hasMedia = p.mediaItemCount > 0,
        )
    }

    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun play(tracks: List<Track>, startIndex: Int) {
        val items = tracks.map { it.toMediaItem() }
        val action: MediaController.() -> Unit = {
            setMediaItems(items, startIndex, 0L); prepare(); play()
        }
        controller?.let { it.action() } ?: run { pending = action; ensureConnected() }
    }

    fun playPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun release() { controller?.release(); controller = null; future = null }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(android.net.Uri.parse(uri))
            .build()
    )
    .build()
