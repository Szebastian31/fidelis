package com.fidelis.player.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.BuildConfig
import com.fidelis.player.MainActivity
import com.fidelis.player.ui.theme.*

private val Online = Color(0xFF4ADE80)

@Composable
fun ProfileScreen(current: Skin, onSkinChange: (Skin) -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val spotifyOn by vm.spotifyConnected.collectAsState()
    val bridgeOn by vm.remoteConnected.collectAsState()
    val bridgeBusy by vm.remoteConnecting.collectAsState()
    val bridgeError by vm.remoteError.collectAsState()
    val lastfmOn by vm.lastfmConnected.collectAsState()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    FidelisBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = chromeBottom()),
        ) {
            ScreenHeader("Profile", eyebrow = "Fidelis")

            SectionLabel("Look")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Skin.entries.forEach { s -> SkinSwatch(s, s == current, Modifier.weight(1f)) { onSkinChange(s) } }
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow the music", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
                        Text("The sky, buttons and glow take on each cover's colour", color = Fidelis.inkMute,
                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    Spacer(Modifier.width(10.dp))
                    FidelisChip(if (Ambience.follow) "On" else "Off", Ambience.follow, { Ambience.setFollow(context, !Ambience.follow) })
                }
            }

            SectionLabel("Connections")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    StatusRow(
                        "Spotify account",
                        if (spotifyOn) "Playlists, search and your library" else "Needed for playlists and search",
                        spotifyOn,
                    ) {
                        if (spotifyOn) FidelisChip("Disconnect", false, vm::disconnectSpotify)
                        else FidelisChip("Connect", true, {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.spotifyAuthUrl())))
                        })
                    }
                    Line()
                    StatusRow(
                        "Playback",
                        when {
                            bridgeOn -> "Connected to the Spotify app"
                            bridgeBusy -> "Connecting…"
                            bridgeError != null -> bridgeError.orEmpty()
                            else -> "Not connected to the Spotify app"
                        },
                        bridgeOn,
                    ) {
                        if (!bridgeOn) FidelisChip(if (bridgeBusy) "Connecting" else "Connect", true, {
                            if (!bridgeBusy) (context as? MainActivity)?.authorizeSpotify()
                        })
                    }
                    Line()
                    StatusRow(
                        "Last.fm",
                        if (lastfmOn) "Scrobbling, mood tags and popularity" else "Sign in to scrobble what you play",
                        lastfmOn,
                    ) {
                        if (lastfmOn) FidelisChip("Disconnect", false, vm::disconnectLastfm)
                    }
                    if (!lastfmOn) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field(user, { user = it }, "Last.fm username")
                            Field(pass, { pass = it }, "Password", password = true,
                                onDone = { vm.connectLastfm(user, pass) })
                            GradientButton(
                                if (vm.lastfmBusy) "Signing in…" else "Sign in to Last.fm",
                                { vm.connectLastfm(user, pass) }, Modifier.fillMaxWidth(),
                                enabled = !vm.lastfmBusy && user.isNotBlank() && pass.isNotBlank(),
                            )
                            vm.lastfmStatus?.let {
                                Text(it, color = Fidelis.pink, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            SoundCard()
            SectionLabel("Keep playing in the background")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ColorOS can freeze Fidelis while the screen is off, which stops Up next after the current song. " +
                            "In Fidelis' app settings, turn on Allow auto launch and Allow background activity, " +
                            "and set Battery to Don't optimise.",
                        color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    SoftButton("Open app settings", {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Settings)
                }
            }

            Text("Fidelis ${BuildConfig.VERSION_NAME}", color = Fidelis.inkFaint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).wrapContentWidth(Alignment.CenterHorizontally))
        }
    }
}

/** A small window onto a look: its sky and glows, a glass card and the accent. */
@Composable
private fun SkinSwatch(s: Skin, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val lift by animateFloatAsState(if (selected) 1f else 0f, tween(320), label = "lift")
    Column(modifier.pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.68f)
                .graphicsLayer { translationY = -4.dp.toPx() * lift }
                .shadow((14 * lift).dp, shape, ambientColor = s.start, spotColor = s.start)
                .clip(shape)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(s.top, s.mid, s.bottom)))
                    drawRect(Brush.radialGradient(listOf(s.glow1, Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.18f), radius = size.width * 1.1f))
                    drawRect(Brush.radialGradient(listOf(s.glow2, Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.55f), radius = size.width * 0.9f))
                }
                .border(if (selected) 2.dp else 1.dp,
                    if (selected) Brush.linearGradient(listOf(s.start, s.end)) else Fidelis.border, shape),
        ) {
            // A tiny glass card and the accent pill, as they'd look in the app.
            val card = RoundedCornerShape(10.dp)
            Column(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth()
                .clip(card).background(Color.White.copy(alpha = 0.10f)).border(1.dp, Fidelis.border, card).padding(6.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(width = 22.dp, height = 7.dp).clip(CircleShape).background(Brush.linearGradient(listOf(s.start, s.end))))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(s.label, color = if (selected) Fidelis.ink else Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
            maxLines = 1)
    }
}

@Composable
private fun StatusRow(title: String, subtitle: String, online: Boolean, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) Online else Fidelis.inkFaint))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun Line() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Fidelis.line))
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String,
    password: Boolean = false, onDone: () -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(shape).background(Fidelis.glass)
            .border(1.dp, Fidelis.line, shape).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, color = Fidelis.inkFaint, style = MaterialTheme.typography.bodyLarge)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fidelis.ink),
            cursorBrush = SolidColor(Fidelis.violet),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                imeAction = if (password) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
