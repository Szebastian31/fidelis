package com.fidelis.player.ui.profile

import android.content. Intent
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
    val remoteConnected by vm.remoteConnected.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()
    val remoteError by vm.remoteError.collectAsState()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        Label("Spotify playback (bridge)")
        Text(
            if (remoteConnected) "Connected to the Spotify app" else "Connecting… open once to approve",
            style = MaterialTheme.typography.bodyMedium,
        )
        nowPlaying?.let {
            Text("${it.title} — ${it.artist}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { vm.testPlay() }, enabled = remoteConnected) { Text("Play test playlist") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.togglePlay() }, enabled = remoteConnected) { Text("Play/Pause") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.next() }, enabled = remoteConnected) { Text("Next") }
        }
        remoteError?.let {
            Text("Bridge error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Label("Skins")
        ChipRow(Skin.entries, current, { it.label }) { onSkinChange(it) }

        Label("Playback defaults")
        Text("Quality", style = MaterialTheme.typography.bodyMedium)
        ChipRow(QualityPref.entries, quality, { it.label }) { vm.setQuality(it) }
        Spacer(Modifier.height(8.dp))
        Text("Crossfade", style = MaterialTheme.typography.bodyMedium)
        ChipRow(CrossfadePref.entries, crossfade, { it.label }) { vm.setCrossfade(it) }

        Label("Spotify import")
        if (!spotifyConnected) {
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.spotifyAuthUrl())))
            }) { Text("Connect Spotify") }
        } else {
            Button(onClick = { vm.importLibrary() }, enabled = !vm.importing) {
                Text(if (vm.importing) "Importing…" else "Import my library")
            }
            TextButton(onClick = { vm.disconnectSpotify() }) { Text("Disconnect") }
        }
        vm.importStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Label("Last.fm scrobbling")
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
            Text("Connected — plays scrobble automatically", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.disconnectLastfm() }) { Text("Disconnect") }
        }
        vm.lastfmStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
