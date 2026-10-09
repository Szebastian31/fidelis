package com.fidelis.player.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.MainActivity
import com.fidelis.player.ui.theme.*

@Composable
fun ProfileScreen(
    current: Skin,
    onSkinChange: (Skin) -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val spotifyConnected by vm.spotifyConnected.collectAsState()
    val lastfmConnected by vm.lastfmConnected.collectAsState()
    val remoteConnected by vm.remoteConnected.collectAsState()
    val remoteConnecting by vm.remoteConnecting.collectAsState()
    val remoteError by vm.remoteError.collectAsState()
    val now by vm.nowPlaying.collectAsState()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    FidelisBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            ScreenHeader(title = "Profile", eyebrow = "Accounts & sound")

            SectionLabel("Spotify")
            SettingsCard {
                StatusRow(
                    title = "Playback",
                    subtitle = when {
                        remoteConnected -> "Connected to the Spotify app"
                        remoteConnecting -> "Connecting… approve in Spotify if asked"
                        else -> "Not connected"
                    },
                    ok = remoteConnected,
                )
                now?.takeIf { remoteConnected && it.title.isNotBlank() }?.let {
                    Text("Playing ${it.title} · ${it.artist}", color = Fidelis.inkMute,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow. Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, top = 6.dp))
                }
                remoteError?.let {
                    Text(it, color = Fidelis.pink, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 20.dp, top = 6.dp))
                }
                if (!remoteConnected) {
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        if (remoteConnecting) "Retry connection" else "Connect playback",
                        { (context as? MainActivity)?.authorizeSpotify() },
                        Modifier.fillMaxWidth(),
                    )
                }

                CardDivider()

                StatusRow(
                    title = "Library",
                    subtitle = if (spotifyConnected) "Playlists and listening history" else "Not connected",
                    ok = spotifyConnected,
                )
                Spacer(Modifier.height(14.dp))
                if (!spotifyConnected) {
                    GradientButton("Connect Spotify", {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.spotifyAuthUrl())))
                    }, Modifier.fillMaxWidth())
                } else {
                    SoftButton("Disconnect", vm::disconnectSpotify, Modifier.fillMaxWidth())
                }
            }

            SectionLabel("Sound")
            SettingsCard {
                Text("Sound quality and crossfade", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fidelis plays through the Spotify app, so these are set in Spotify. " +
                        "For your FreeBuds, set Audio quality to Very High or Lossless, and turn on " +
                        "Crossfade or Automix under Playback. Fidelis still orders mood mixes so they flow.",
                    color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                SoftButton("Open Spotify", {
                    context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                        ?.let { context.startActivity(it) }
                }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }

            SectionLabel("Last.fm")
            SettingsCard {
                if (!lastfmConnected) {
                    StatusRow("Scrobbling", "Sign in to log what you play", ok = false)
                    Spacer(Modifier.height(14.dp))
                    FidelisField(user, { user = it }, "Username")
                    Spacer(Modifier.height(10.dp))
                    FidelisField(pass, { pass = it }, "Password", password = true)
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        if (vm.lastfmBusy) "Signing in…" else "Connect Last.fm",
                        { vm.connectLastfm(user, pass); pass = "" },
                        Modifier.fillMaxWidth(),
                        enabled = !vm.lastfmBusy && user.isNotBlank() && pass.isNotBlank(),
                    )
                } else {
                    StatusRow("Scrobbling", "Connected to Last.fm", ok = true)
                    Spacer(Modifier.height(14.dp))
                    SoftButton("Disconnect", vm::disconnectLastfm, Modifier.fillMaxWidth())
                }
                vm.lastfmStatus?.let {
                    Text(it, color = Fidelis.pink, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }

            SectionLabel("Theme")
            SettingsCard {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skin.entries.forEach { s -> FidelisChip(s.label, s == current, { onSkinChange(s) }) }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun StatusRow(title: String, subtitle: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Color(0xFF4ADE80) else Fidelis.inkFaint))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CardDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(Fidelis.line))
}

@Composable
private fun FidelisField(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Fidelis.ink, unfocusedTextColor = Fidelis.ink,
            focusedBorderColor = Fidelis.violet, unfocusedBorderColor = Fidelis.line,
            focusedLabelColor = Fidelis.violet, unfocusedLabelColor = Fidelis.inkMute,
            cursorColor = Fidelis.violet,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
