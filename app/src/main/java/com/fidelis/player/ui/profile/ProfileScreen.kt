package com.fidelis.player.ui.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fidelis.player.ui.theme.Skin

/**
 * STUB. Full build adds: Playback Defaults (music quality + crossfade selectors, lyrics
 * toggle), and Integrations (Spotify import, Last.fm connect). Skin picker is live below.
 */
@Composable
fun ProfileScreen(current: Skin, onSkinChange: (Skin) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Text("Skins", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Skin.entries.forEach { skin ->
                FilterChip(
                    selected = skin == current,
                    onClick = { onSkinChange(skin) },
                    label = { Text(skin.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Text("TODO: music quality + crossfade selectors, integrations", style = MaterialTheme.typography.bodyMedium)
    }
}
