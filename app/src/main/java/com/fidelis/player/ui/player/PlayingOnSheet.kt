package com.fidelis.player.ui.player

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.audio.SoundEngine
import com.fidelis.player.integration.spotify.ConnectDevice
import com.fidelis.player.integration.spotify.DeviceList
import com.fidelis.player.integration.spotify.SpotifyConnect
import com.fidelis.player.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow. MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayingOnViewModel @Inject constructor(private val connect: SpotifyConnect) : ViewModel() {
    private val _list = MutableStateFlow<DeviceList>(DeviceList.Loading)
    val list = _list.asStateFlow()
    var switching by mutableStateOf<String?>(null)
        private set

    fun refresh() { viewModelScope.launch { _list.value = connect.devices() } }

    fun transfer(d: ConnectDevice, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            switching = d.id
            val ok = connect.transfer(d.id)
            delay(800) // Spotify takes a moment to report the new active device
            _list.value = connect.devices()
            switching = null
            done(ok)
        }
    }

    fun reconnectUrl() = connect.reconnectUrl()
}

/** Where the music plays: this phone's output (speaker / Bluetooth / wired) and Spotify Connect devices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayingOnSheet(onDismiss: () -> Unit, vm: PlayingOnViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val list by vm.list.collectAsState()
    val output by SoundEngine.device.collectAsState()
    LaunchedEffect(Unit) { SoundEngine.init(context); vm.refresh() }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Fidelis.bg1,
        contentColor = Fidelis.ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp)
                .clip(CircleShape).background(Fidelis.inkFaint))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Playing on", color = Fidelis.ink,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            Spacer(Modifier.height(4.dp))
            Text("Move the music to another speaker, or change where this phone plays it.",
                color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(20.dp))
            Label("THIS PHONE")
            DeviceRow(
                icon = if (output.key == "speaker") Icons.Rounded.Smartphone else Icons.Rounded.Headphones,
                title = output.label,
                subtitle = "Speaker, Bluetooth or wired",
                trailing = { Text("Change", color = Fidelis.violet, style = MaterialTheme.typography.labelLarge) },
                onClick = { openOutputSwitcher(context) },
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label("SPOTIFY CONNECT") }
                Box(Modifier.size(36.dp).pressable { vm.refresh() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Refresh, "Refresh", tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
                }
            }
            when (val l = list) {
                DeviceList.Loading -> repeat(3) {
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.size(44.dp), radius = 14.dp)
                        Spacer(Modifier.width(14.dp))
                        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(12.dp), radius = 6.dp)
                    }
                }
                DeviceList.SignedOut -> Note("Connect Spotify in Profile to see your other speakers and screens.")
                DeviceList.Failed -> Note("Couldn't reach Spotify. Tap refresh to try again.")
                DeviceList.NeedsReconnect -> {
                    Note("Fidelis needs one more Spotify permission to see your devices. Sign in once more and approve it.")
                    Spacer(Modifier.height(10.dp))
                    GradientButton(
                        text = "Reconnect Spotify", icon = Icons.Rounded.Link,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.reconnectUrl()))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is DeviceList. Ready ->
                    if (l.devices.isEmpty()) {
                        Note("No devices found. Open Spotify on your TV, speaker or computer and it will show up here.")
                    } else l.devices.forEach { d ->
                        DeviceRow(
                            icon = iconFor(d.type),
                            title = d.name,
                            subtitle = when {
                                d.active -> "Playing now"
                                d.restricted -> "Can't be controlled from here"
                                else -> d.type.lowercase().replaceFirstChar { it.uppercase() }
                            },
                            highlighted = d.active,
                            enabled = !d.restricted,
                            trailing = {
                                when {
                                    vm.switching == d.id -> CircularProgressIndicator(
                                        Modifier.size(20.dp), color = Fidelis.violet, strokeWidth = 2.dp)
                                    d.active -> Icon(Icons.Rounded.GraphicEq, null, tint = Fidelis.violet)
                                }
                            },
                            onClick = {
                                if (d.active) toast("Already playing on ${d.name}")
                                else vm.transfer(d) { ok -> toast(if (ok) "Playing on ${d.name}" else "${d.name} didn't respond") }
                            },
                        )
                    }
            }
            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun DeviceRow(
    icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit,
    highlighted: Boolean = false, enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .then(if (highlighted) Modifier.background(Fidelis.glass) else Modifier)
            .pressable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) Fidelis.accent else Fidelis.glass), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Fidelis.ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (highlighted) Fidelis.violet else Fidelis.ink,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        trailing()
    }
}

private fun iconFor(type: String): ImageVector = when (type.lowercase()) {
    "computer" -> Icons.Rounded.Computer
    "smartphone" -> Icons.Rounded.Smartphone
    "tablet" -> Icons.Rounded.TabletAndroid
    "tv" -> Icons.Rounded.Tv
    "speaker" -> Icons.Rounded.Speaker
    "castaudio", "castvideo" -> Icons.Rounded.Cast
    "avr", "stb", "audiodongle" -> Icons.Rounded.SpeakerGroup
    "gameconsole" -> Icons.Rounded.SportsEsports
    "automobile" -> Icons.Rounded.DirectionsCar
    else -> Icons.Rounded.Devices
}

/** Android's own output picker for Spotify's sound; falls back to Bluetooth settings. */
private fun openOutputSwitcher(context: Context) {
    val panel = Intent("com.android.settings.panel.action. MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", "com.spotify.music")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(panel) }.isSuccess) return
    if (Build.VERSION.SDK_INT >= 34 &&
        runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)) return
    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
