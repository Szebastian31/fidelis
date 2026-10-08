package com.fidelis.player.integration.spotify

import android.content.Context
import com.fidelis.player.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRemote @Inject constructor() {

    data class Snapshot(
        val title: String, val artist: String, val isPaused: Boolean,
        val positionMs: Long, val durationMs: Long, val imageUri: String?, val uri: String?,
    )

    private var appRemote: SpotifyAppRemote? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // lazy custom-order queue (for mood playlists)
    private val planned = ArrayList<String>()
    private val queued = HashSet<String>()
    private val ahead = 3

    fun connect(context: Context) {
        if (appRemote?.isConnected == true) return
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri("fidelis://auth").showAuthView(true).build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote; _connected.value = true; _error.value = null
                remote.playerApi.subscribeToPlayerState().setEventCallback { ps: PlayerState ->
                    val t = ps.track
                    _state.value = Snapshot(
                        title = t?.name ?: "", artist = t?.artist?.name ?: "",
                        isPaused = ps.isPaused, positionMs = ps.playbackPosition,
                        durationMs = t?.duration ?: 0L, imageUri = t?.imageUri?.raw, uri = t?.uri,
                    )
                    t?.uri?.let { topUp(it) }  // keep ~3 ahead queued for custom orders
                }
            }
            override fun onFailure(error: Throwable) { _connected.value = false; _error.value = error.message }
        })
    }

    fun playContext(uri: String) { planned.clear(); queued.clear(); appRemote?.playerApi?.play(uri) }

    /** Play a custom-ordered list, feeding Spotify ~3 tracks ahead as it progresses. */
    fun playQueue(uris: List<String>) {
        if (uris.isEmpty()) return
        planned.clear(); planned.addAll(uris); queued.clear()
        appRemote?.playerApi?.play(uris.first())
        queued.add(uris.first())
        topUp(uris.first())
    }

    private fun topUp(currentUri: String) {
        val idx = planned.indexOf(currentUri)
        if (idx < 0) return
        var added = 0; var i = idx + 1
        while (i < planned.size && added < ahead) {
            val u = planned[i]
            if (queued.add(u)) { appRemote?.playerApi?.queue(u); added++ }
            i++
        }
    }

    fun resume() { appRemote?.playerApi?.resume() }
    fun pause() { appRemote?.playerApi?.pause() }
    fun skipNext() { appRemote?.playerApi?.skipNext() }
    fun skipPrevious() { appRemote?.playerApi?.skipPrevious() }

    fun disconnect() {
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null; _connected.value = false
    }
}
