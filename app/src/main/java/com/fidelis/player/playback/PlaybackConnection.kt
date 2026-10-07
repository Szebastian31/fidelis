package com.fidelis.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fidelis.player.data.model.Track
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the UI to the PlaybackService's MediaSession. Starting playback here makes
 * Media3 post the system media notification (the "pill") automatically.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var pending: (MediaController.() -> Unit)? = null

    private fun ensureConnected() {
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            controller = f.get()
            pending?.let { controller!!.it() }
            pending = null
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        val items = tracks.map { it.toMediaItem() }
        val action: MediaController.() -> Unit = {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
        controller?.let { it.action() } ?: run { pending = action; ensureConnected() }
    }

    fun release() {
        controller?.release()
        controller = null
        future = null
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title).setArtist(artist).setAlbumTitle(album).build()
    )
    .build()
