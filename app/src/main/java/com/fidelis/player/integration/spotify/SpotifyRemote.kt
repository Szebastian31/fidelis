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

/**
 * The "bridge": remote-controls the installed Spotify app, which does the actual
 * playback. Requires Premium + the Spotify app, and the package/SHA-1 registered
 * in the Spotify dashboard. Connect while the UI is in the foreground.
 */
@Singleton
class SpotifyRemote @Inject constructor() {

    data class Snapshot(
        val title: String,
        val artist: String,
        val isPaused: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val imageUri: String?,
    )

    private var appRemote: SpotifyAppRemote? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connect(context: Context) {
        if (appRemote?.isConnected == true) return
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri("fidelis://auth")
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote
                _connected.value = true
                _error.value = null
                remote.playerApi.subscribeToPlayerState().setEventCallback { ps: PlayerState ->
                    val t = ps.track
                    _state.value = Snapshot(
                        title = t?.name ?: "",
                        artist = t?.artist?.name ?: "",
                        isPaused = ps.isPaused,
                        positionMs = ps.playbackPosition,
                        durationMs = t?.duration ?: 0L,
                        imageUri = t?.imageUri?.raw,
                    )
                }
            }
            override fun onFailure(error: Throwable) {
                _connected.value = false
                _error.value = error.message
            }
        })
    }

    fun playContext(uri: String) { appRemote?.playerApi?.play(uri) }
    fun queue(uri: String) { appRemote?.playerApi?.queue(uri) }
    fun resume() { appRemote?.playerApi?.resume() }
    fun pause() { appRemote?.playerApi?.pause() }
    fun skipNext() { appRemote?.playerApi?.skipNext() }
    fun skipPrevious() { appRemote?.playerApi?.skipPrevious() }

    fun disconnect() {
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _connected.value = false
    }
}
