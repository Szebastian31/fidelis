package com.fidelis.player.radio

import android.content.ComponentName
import android.content.Context
import android.widget. Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content. ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common. MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The radio, for any screen: what's on, and play, pause and stop. Talks to RadioService. */
object Radio {
    var station by mutableStateOf<Station?>(null); private set
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    /** The song on air, when the station sends it. */
    var onAir by mutableStateOf<String?>(null)

    private var controller: MediaController? = null
    private var connecting = false
    private val waiting = mutableListOf<(MediaController) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var app: Context? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            playing = player.isPlaying
            buffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
        }
        override fun onPlayerError(error: PlaybackException) {
            buffering = false
            app?.let { Toast.makeText(it, "This station isn't answering. Try another one.", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
        val ctx = context.applicationContext
        app = ctx
        controller?.let { block(it); return }
        waiting += block
        if (connecting) return
        connecting = true
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, RadioService::class.java))).buildAsync()
        f.addListener({
            connecting = false
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            waiting.toList().forEach { it(c) }
            waiting.clear()
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun play(context: Context, s: Station) {
        station = s; onAir = null; buffering = true
        RadioLibrary.played(context, s)
        scope.launch { RadioBrowser.click(s.id) }
        val meta = MediaMetadata.Builder().setTitle(s.name).setArtist(s.meta.ifBlank { "Live radio" })
            .apply { s.favicon.takeIf { it.isNotBlank() }?.let { setArtworkUri(it.toUri()) } }.build()
        val item = MediaItem.Builder().setMediaId(s.id).setUri(s.stream.toUri()).setMediaMetadata(meta)
            .apply { if (s.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        withController(context) { c -> c.setMediaItem(item); c.prepare(); c.play() }
    }

    /** Pause, or pick the broadcast up again live rather than where you left it. */
    fun toggle(context: Context) = withController(context) { c ->
        if (c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)) c.pause()
        else if (c.mediaItemCount > 0) { c.stop(); c.prepare(); c.play() }
    }

    fun stop() {
        controller?.run { stop(); clearMediaItems() }
        station = null; onAir = null; playing = false; buffering = false
    }
}
