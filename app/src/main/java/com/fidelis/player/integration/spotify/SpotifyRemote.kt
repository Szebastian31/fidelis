package com.fidelis.player.integration.spotify

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import com.fidelis.player.BuildConfig
import com.fidelis.player.playback.PlaybackService
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class QueueTrack(val uri: String, val title: String, val artist: String)

@Singleton
class SpotifyRemote @Inject constructor() {
    data class Snapshot(
        val title: String, val artist: String, val isPaused: Boolean,
        val positionMs: Long, val durationMs: Long, val imageUri: String?, val uri: String?,
    )

    private var appRemote: SpotifyAppRemote? = null
    private var appContext: Context? = null

    private val _connected = MutableStateFlow(false); val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _connecting = MutableStateFlow(false); val connecting: StateFlow<Boolean> = _connecting.asStateFlow()
    private val _state = MutableStateFlow<Snapshot?>(null); val state: StateFlow<Snapshot?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error.asStateFlow()
    private val _queue = MutableStateFlow<List<QueueTrack>>(emptyList()); val queue: StateFlow<List<QueueTrack>> = _queue.asStateFlow()
    private val _artwork = MutableStateFlow<Bitmap?>(null); val artwork: StateFlow<Bitmap?> = _artwork.asStateFlow()

    private val planned = ArrayList<String>()
    private val queued = HashSet<String>()
    private val ahead = 3
    private var lastArtUri: String? = null

    fun markConnecting() { _connecting.value = true; _error.value = null }
    fun reportAuthError(message: String?) { _connecting.value = false; _error.value = message ?: "Authorization failed" }

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (appRemote?.isConnected == true) { _connected.value = true; _connecting.value = false; return }
        _connecting.value = true
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri("fidelis://auth").showAuthView(false).build()
        try {
            SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    appRemote = remote; _connected.value = true; _connecting.value = false; _error.value = null
                    remote.playerApi.subscribeToPlayerState().setEventCallback { ps: PlayerState ->
                        val t = ps.track
                        _state.value = Snapshot(
                            title = t?.name ?: "", artist = t?.artist?.name ?: "",
                            isPaused = ps.isPaused, positionMs = ps.playbackPosition,
                            durationMs = t?.duration ?: 0L, imageUri = t?.imageUri?.raw, uri = t?.uri,
                        )
                        t?.uri?.let { topUp(it) }
                        if (t?.imageUri != null && t.imageUri.raw != lastArtUri) {
                            lastArtUri = t.imageUri.raw
                            remote.imagesApi.getImage(t.imageUri).setResultCallback { bmp -> _artwork.value = bmp }
                        }
                    }
                }
                override fun onFailure(error: Throwable) {
                    Log.e("FidelisBridge", "failure", error)
                    _connected.value = false; _connecting.value = false
                    _error.value = error.message ?: error.javaClass.simpleName
                }
            })
        } catch (t: Throwable) {
            _connecting.value = false; _error.value = "connect(): ${t.message ?: t.javaClass.simpleName}"
        }
    }

    fun playContext(uri: String) {
        _queue.value = emptyList(); planned.clear(); queued.clear()
        appRemote?.playerApi?.play(uri); ensureService()
    }

    fun playQueue(items: List<QueueTrack>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        _queue.value = items
        planned.clear(); planned.addAll(items.map { it.uri }); queued.clear()
        val start = items.getOrElse(startIndex) { items.first() }
        appRemote?.playerApi?.play(start.uri); queued.add(start.uri); topUp(start.uri); ensureService()
    }

    fun reshuffle() {
        val current = _state.value?.uri
        val items = _queue.value.toMutableList()
        if (items.isEmpty()) return
        items.shuffle()
        val idx = items.indexOfFirst { it.uri == current }
        if (idx > 0) items.add(0, items.removeAt(idx))
        playQueue(items, 0)
    }

    private fun topUp(currentUri: String) {
        val idx = planned.indexOf(currentUri); if (idx < 0) return
        var added = 0; var i = idx + 1
        while (i < planned.size && added < ahead) {
            val u = planned[i]; if (queued.add(u)) { appRemote?.playerApi?.queue(u); added++ }; i++
        }
    }

    private fun ensureService() {
        val ctx = appContext ?: return
        ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
    }

    fun resume() { appRemote?.playerApi?.resume() }
    fun pause() { appRemote?.playerApi?.pause() }
    fun skipNext() { appRemote?.playerApi?.skipNext() }
    fun skipPrevious() { appRemote?.playerApi?.skipPrevious() }
    fun seekTo(ms: Long) { appRemote?.playerApi?.seekTo(ms) }

    fun disconnect() {
        appRemote?.let { SpotifyAppRemote.disconnect(it) }; appRemote = null; _connected.value = false
        appContext?.let { it.stopService(Intent(it, PlaybackService::class.java)) }
    }
}
