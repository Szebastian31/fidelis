package com.fidelis.player.ui.shuffle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SmartShuffleScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: SmartShuffleViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { if (vm.rows.isEmpty()) vm.generate() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Smart Shuffle", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.generate() }) { Text("Reshuffle") }
        }
        vm.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.play(); onOpenPlayer() },
            enabled = vm.rows.isNotEmpty() && !vm.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.rows.isEmpty()) "Building…" else "Play ${vm.rows.size} tracks") }

        Spacer(Modifier.height(12.dp))
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(Modifier.weight(1f)) {
            items(vm.rows) { row ->
                ListItem(
                    headlineContent = { Text(row.track.title) },
                    supportingContent = { Text(row.track.artist) },
                    trailingContent = { AssistChip(onClick = {}, label = { Text("${row.why} · ${row.confidence}") }) },
                )
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
