package com.fidelis.player.ui.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.spotify.SpotifyKey
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val spotify: SpotifyRepository,
    private val lastfm: LastfmRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val remoteConnected: StateFlow<Boolean> = remote.connected
    val remoteConnecting: StateFlow<Boolean> = remote.connecting
    val remoteError: StateFlow<String?> = remote.error
    fun connectBridge(context: Context) = remote.connect(context)

    // ---- Which Spotify app Fidelis signs in as ----
    var ownClientId by mutableStateOf(SpotifyKey.own(app)); private set
    val keySet: Boolean get() = ownClientId != null || SpotifyKey.builtIn
    val signingSha1: String? by lazy { SpotifyKey.signingSha1(app) }

    /** Saves a pasted Client ID (null forgets it). Sign-ins belong to one Spotify app, so both are reset. */
    fun setClientId(id: String?): Boolean {
        if (id != null && !SpotifyKey.valid(id)) return false
        SpotifyKey.setOwn(app, id)
        ownClientId = SpotifyKey.own(app)
        spotify.disconnect()
        remote.disconnect()
        return true
    }

    val spotifyConnected: StateFlow<Boolean> = spotify.connected
    fun spotifyAuthUrl(): String = spotify.authUrl()
    fun disconnectSpotify() = spotify.disconnect()

    val lastfmConnected: StateFlow<Boolean> = lastfm.connected
    var lastfmBusy by mutableStateOf(false); private set
    var lastfmStatus by mutableStateOf<String?>(null); private set

    fun connectLastfm(username: String, password: String) {
        if (lastfmBusy || username.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            lastfmBusy = true; lastfmStatus = "Signing in…"
            val ok = lastfm.connect(username.trim(), password)
            lastfmStatus = if (ok) null else "Sign-in failed. Check your username and password."
            lastfmBusy = false
        }
    }

    fun disconnectLastfm() { lastfm.disconnect(); lastfmStatus = null }
}
