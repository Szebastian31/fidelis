package com.fidelis.player.ui.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val spotify: SpotifyRepository,
    private val lastfm: LastfmRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val remoteConnected: StateFlow<Boolean> = remote.connected
    val remoteConnecting: StateFlow<Boolean> = remote.connecting
    val remoteError: StateFlow<String?> = remote.error
    fun connectBridge(context: Context) = remote.connect(context)

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
