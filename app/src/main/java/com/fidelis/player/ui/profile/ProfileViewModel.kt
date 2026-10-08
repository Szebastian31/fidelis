package com.fidelis.player.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.data.settings.CrossfadePref
import com.fidelis.player.data.settings.PlaybackSettings
import com.fidelis.player.data.settings.QualityPref
import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.playback.crossfade.CrossfadeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val spotify: SpotifyRepository,
    private val lastfm: LastfmRepository,
    private val settings: PlaybackSettings,
    private val crossfadeEngine: CrossfadeEngine,
    private val remote: SpotifyRemote,
) : ViewModel() {

    // Spotify Web API (import)
    val spotifyConnected: StateFlow<Boolean> = spotify.connected
    var importing by mutableStateOf(false); private set
    var importStatus by mutableStateOf<String?>(null); private set

    fun spotifyAuthUrl(): String = spotify.authUrl()
    fun importLibrary() {
        if (importing) return
        viewModelScope.launch {
            importing = true; importStatus = "Importing…"
            val r = spotify.importLibrary { c -> importStatus = "Fetched $c liked tracks…" }
            importStatus = "Imported ${r.total} • matched ${r.matched} • ${r.gaps} gaps"
            importing = false
        }
    }
    fun disconnectSpotify() { spotify.disconnect(); importStatus = null }

    // Spotify bridge (playback)
    val remoteConnected: StateFlow<Boolean> = remote.connected
    val nowPlaying: StateFlow<SpotifyRemote.Snapshot?> = remote.state
    val remoteError: StateFlow<String?> = remote.error

    fun testPlay() = remote.playContext("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M") // Today's Top Hits
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }
    fun next() = remote.skipNext()

    // Last.fm
    val lastfmConnected: StateFlow<Boolean> = lastfm.connected
    var lastfmBusy by mutableStateOf(false); private set
    var lastfmStatus by mutableStateOf<String?>(null); private set

    fun connectLastfm(username: String, password: String) {
        if (lastfmBusy || username.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            lastfmBusy = true; lastfmStatus = "Signing in…"
            val ok = lastfm.connect(username.trim(), password)
            lastfmStatus = if (ok) null else "Sign-in failed — check your details."
            lastfmBusy = false
        }
    }
    fun disconnectLastfm() { lastfm.disconnect(); lastfmStatus = null }

    // Playback defaults
    val quality: StateFlow<QualityPref> = settings.quality
    val crossfade: StateFlow<CrossfadePref> = settings.crossfade
    fun setQuality(q: QualityPref) = settings.setQuality(q)
    fun setCrossfade(c: CrossfadePref) {
        settings.setCrossfade(c)
        crossfadeEngine.userPreferenceMs = c.ms
    }
}
