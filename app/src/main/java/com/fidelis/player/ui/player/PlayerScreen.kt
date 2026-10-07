package com.fidelis.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fidelis.player.data.model.Fidelity

/**
 * STUB showcase. Wire this to a PlayerViewModel exposing the current Track + position from
 * the MediaController. Shown here: artwork, title/artist, and the fidelity badge driven by
 * the real [Fidelity] read at scan time.
 */
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    // Placeholder data; replace with ViewModel state.
    val fidelity = Fidelity(codec = "FLAC", bitDepth = 24, sampleRateHz = 96000, bitrateKbps = null, isLossless = true)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
        ) { Text("", Modifier.padding(80.dp)) }

        Text("Midnight Lull", style = MaterialTheme.typography.headlineSmall)
        Text("Odeline", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)

        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(fidelity.badge()) })
        }
        // TODO: scrubber, transport controls, Smart Shuffle, optional lyrics, scrobble state.
    }
}
