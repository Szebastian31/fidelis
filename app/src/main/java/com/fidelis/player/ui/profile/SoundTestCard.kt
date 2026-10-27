package com.fidelis.player.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fidelis.player.audio.SoundSession
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.delay

/** Checks whether Fidelis can shape Spotify's sound on this phone. */
@Composable
fun SoundTestCard() {
    val spotify by SoundSession.spotify.collectAsState()
    val last by SoundSession.lastSeen.collectAsState()
    var result by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) { if (running) { delay(5_000); running = false } }

    SectionLabel("Sound test")
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    spotify != null -> "Spotify announced its audio session (#${spotify!!.session})."
                    last != null -> "Spotify hasn't announced its session. ${last!!.packageName} did, so announcements reach Fidelis."
                    else -> "Spotify hasn't announced its audio session yet."
                },
                color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Play a song in Spotify, then tap Test. For 5 seconds the treble is cut, so it should sound clearly muffled. " +
                    "If it doesn't change at all, Spotify is blocking outside sound effects on this phone.",
                color = Fidelis.inkSoft, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            GradientButton(
                if (running) "Listen now…" else "Test for 5 seconds",
                { result = SoundSession.test(); running = true },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.GraphicEq, enabled = !running,
            )
            result?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
