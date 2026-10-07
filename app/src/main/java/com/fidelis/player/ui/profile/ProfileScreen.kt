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
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        Text("Skins", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
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

        Text("Spotify", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(8.dp))
        if (!spotifyConnected) {
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.spotifyAuthUrl())))
            }) { Text("Connect Spotify") }
        } else {
            Text("Connected", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.importLibrary() }, enabled = !vm.importing) {
                Text(if (vm.importing) "Importing…" else "Import my library")
            }
            TextButton(onClick = { vm.disconnectSpotify() }) { Text("Disconnect") }
        }
        vm.importStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("Last.fm scrobbling", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp))
        Spacer(Modifier.height(8.dp))
        if (!lastfmConnected) {
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Password") }, singleLine = true,
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
        vm.lastfmStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
    }
}
