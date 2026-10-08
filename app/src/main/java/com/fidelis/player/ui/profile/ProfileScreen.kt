package com.fidelis.player.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.data.settings.CrossfadePref
import com.fidelis.player.data.settings.QualityPref
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.GlassCard
import com.fidelis.player.ui.theme.Skin

@Composable
fun ProfileScreen(
    current: Skin,
    onSkinChange: (Skin) -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val spotifyConnected by vm.spotifyConnected.collectAsState()
    val lastfmConnected by vm.lastfmConnected.collectAsState()
    val quality by vm.quality.collectAsState()
    val crossfade by vm.crossfade.collectAsState()
    val remoteConnecting by vm.remoteConnecting.collectAsState()
    val remoteConnected by vm.remoteConnected.collectAsState()
    val remoteError by vm.remoteError.collectAsState()
    val now by vm.nowPlaying.collectAsState()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    FidelisBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Profile", color = Fidelis.ink, style = MaterialTheme.typography.headlineMedium)

            Section("Spotify playback") {
                Text(
                    when {
                        remoteConnected -> "Connected to the Spotify app"
                        remoteConnecting -> "Connecting…"
                        else -> "Not connected — tap Connect, then approve in Spotify."
                    },
                    color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium,
                )
                now?.let {
                    Text("${it.title} — ${it.artist}", color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
                }
                if (!remoteConnected) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.connectBridge(context) }) {
                        Text(if (remoteConnecting) "Retry connect" else "Connect playback")
                    }
                }
                remoteError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Bridge error: $it", color = Fidelis.pink, style = MaterialTheme.typography.labelSmall)
                }
            }

            Section("Playback defaults") {
                Text("Quality", color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium)
                ChipRow(QualityPref.entries, quality, { it.label }) { vm.setQuality(it) }
                Spacer(Modifier.height(8.dp))
                Text("Crossfade", color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium)
                ChipRow(CrossfadePref.entries, crossfade, { it.label }) { vm.setCrossfade(it) }
            }

            Section("Last.fm scrobbling") {
                if (!lastfmConnected) {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.connectLastfm(user, pass); pass = "" }, enabled = !vm.lastfmBusy) {
                        Text(if (vm.lastfmBusy) "Signing in…" else "Connect Last.fm")
                    }
                } else {
                    Text("Connected — plays scrobble automatically", color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.disconnectLastfm() }) { Text("Disconnect") }
                }
                vm.lastfmStatus?.let { Text(it, color = Fidelis.pink, style = MaterialTheme.typography.bodySmall) }
            }

            Section("Skins") {
                ChipRow(Skin.entries, current, { it.label }) { onSkinChange(it) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Fidelis.inkMute, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected, onClick = { onSelect(opt) },
                label = { Text(label(opt)) }, modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
