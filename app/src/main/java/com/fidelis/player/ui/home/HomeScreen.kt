package com.fidelis.player.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * STUB. Build out the browse rows (Quick picks, Listen again, Lossless gems, Heavy rotation)
 * and the search field, backed by MusicDao.observeTracks(). Tapping a card opens the player.
 */
@Composable
fun HomeScreen(onOpenPlayer: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Text("TODO: search + browse rows", style = MaterialTheme.typography.bodyMedium)
    }
}
