package com.fidelis.player.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.data.model.Track

@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onSmartShuffle: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsState()
    val results by vm.results.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Home",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Search your library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        FilledTonalButton(onClick = onSmartShuffle, modifier = Modifier.fillMaxWidth()) {
            Text("Smart Shuffle")
        }
        Spacer(Modifier.height(12.dp))

        if (vm.query.isNotBlank()) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(results) { index, t ->
                    ResultRow(t) { vm.play(results, index); onOpenPlayer() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Section("Quick picks for you", rows.quickPicks, onOpenPlayer, vm)
                Section("Listen again", rows.listenAgain, onOpenPlayer, vm)
                Section("Lossless gems", rows.losslessGems, onOpenPlayer, vm)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, list: List<Track>, onOpenPlayer: () -> Unit, vm: HomeViewModel) {
    if (list.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(list) { index, t ->
            HomeCard(t) { vm.play(list, index); onOpenPlayer() }
        }
    }
}

@Composable
private fun HomeCard(t: Track, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Surface(
            Modifier.size(132.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Spacer(Modifier.height(6.dp))
        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            t.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResultRow(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
