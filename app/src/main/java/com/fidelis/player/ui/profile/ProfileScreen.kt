package com.fidelis.player.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val connected by vm.connected.collectAsState()

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

        Text("Integrations", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(8.dp))

        if (!connected) {
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl())))
            }) { Text("Connect Spotify") }
        } else {
            Text("Spotify connected", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.importLibrary() }, enabled = !vm.importing) {
                Text(if (vm.importing) "Importing…" else "Import my library")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { vm.disconnect() }) { Text("Disconnect") }
        }

        vm.status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
