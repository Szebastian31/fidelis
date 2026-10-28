package com.fidelis.player.ui.playlists

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Playlists pinned to the front of Your playlists, newest pin first. Kept on the phone. */
@Singleton
class PlaylistPins @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fidelis_pins", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    fun toggle(id: String) {
        val now = if (id in _ids.value) _ids.value - id else listOf(id) + _ids.value
        _ids.value = now
        prefs.edit().putString("ids", now.joinToString(",")).apply()
    }
}
