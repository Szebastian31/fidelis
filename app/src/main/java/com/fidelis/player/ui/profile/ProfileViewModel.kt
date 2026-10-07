package com.fidelis.player.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val spotify: SpotifyRepository,
) : ViewModel() {

    val connected: StateFlow<Boolean> = spotify.connected

    var importing by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    fun authUrl(): String = spotify.authUrl()

    fun importLibrary() {
        if (importing) return
        viewModelScope.launch {
            importing = true
            status = "Importing…"
            val r = spotify.importLibrary { count -> status = "Fetched $count liked tracks…" }
            status = "Imported ${r.total} • matched ${r.matched} • ${r.gaps} gaps"
            importing = false
        }
    }

    fun disconnect() { spotify.disconnect(); status = null }
}
