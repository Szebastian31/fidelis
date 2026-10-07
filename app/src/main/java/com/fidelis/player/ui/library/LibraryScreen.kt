package com.fidelis.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * STUB. Liked / Playlists / Albums segmented list from MusicDao.observeTracks(),
 * each row showing the real fidelity badge (green lossless / amber "320k").
 */
@Composable
fun LibraryScreen(onOpenPlayer: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Text("TODO: segmented track list with fidelity badges", style = MaterialTheme.typography.bodyMedium)
    }
}
