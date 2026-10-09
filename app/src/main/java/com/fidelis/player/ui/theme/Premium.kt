package com.fidelis.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.SpotifyRemote

/** Design tokens. Every screen pulls colour from here, never ad-hoc. */
object Fidelis {
    val bg0 = Color(0xFF080B1F); val bg1 = Color(0xFF12183D); val bg2 = Color(0xFF1E2252)

    // Accent colours follow the selected theme (see Skin / FidelisTheme).
    var purple by mutableStateOf(Skin.Midnight.start); private set
    var pink by mutableStateOf(Skin.Midnight.end); private set
    var violet by mutableStateOf(Skin.Midnight.highlight); private set

    val orange = Color(0xFFFF9E5A); val sky = Color(0xFF7CC4F5)
    val ink = Color(0xFFFFFFFF); val inkSoft = Color(0xD9FFFFFF)
    val inkMute = Color(0x8CFFFFFF); val inkFaint = Color(0x52FFFFFF)
    val glass = Color(0x14FFFFFF); val glassStrong = Color(0x26FFFFFF); val line = Color(0x1FFFFFFF)
    val border = Brush.verticalGradient(listOf(Color(0x3DFFFFFF), Color(0x0AFFFFFF)))
    val skyGradient = Brush.verticalGradient(listOf(bg0, bg1, bg2))
    val accent: Brush get() = Brush.linearGradient(listOf(purple, pink))

    fun applySkin(s: Skin) { purple = s.start; pink = s.end; violet = s.highlight }
}

/** Midnight gradient with two soft colour glows: the backdrop for every screen. */
@Composable
fun FidelisBackground(content: @Composable () -> Unit) {
    val glowA = Fidelis.purple
    val glowB = Fidelis.pink
    Box(
        Modifier.fillMaxSize().background(Fidelis.skyGradient).drawBehind {
            drawRect(Brush.radialGradient(
                listOf(glowA.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(size.width * 0.15f, 0f), radius = size.width * 0.95f,
            ))
            drawRect(Brush.radialGradient(
                listOf(glowB.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width, size.height * 0.78f), radius = size.width * 0.85f,
            ))
        }
    ) { content() }
}

/** Gentle scale-down on press instead of a ripple. Used by every tappable surface. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 22.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(Fidelis.glass).border(1.dp, Fidelis.border, shape)) { content() }
}

@Composable
fun ScreenHeader(title: String, eyebrow: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(it.uppercase(), color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, color = Fidelis.ink, style = MaterialTheme.typography.headlineMedium)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(top = 24.dp, bottom = 10.dp))
}

@Composable
fun GradientButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true,
) {
    Row(
        modifier.height(52.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape)
            .background(Fidelis.accent).pressable(enabled, onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, tint = Fidelis.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true,
) {
    Row(
        modifier.height(52.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape)
            .background(Fidelis.glassStrong).border(1.dp, Fidelis.line, CircleShape)
            .pressable(enabled, onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, tint = Fidelis.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun FidelisChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) Modifier.background(Fidelis.accent)
             else Modifier.background(Fidelis.glass).border(1.dp, Fidelis.line, CircleShape)
    Box(modifier.clip(CircleShape).then(bg).pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(label, color = if (selected) Fidelis.ink else Fidelis.inkSoft,
            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconCircle(
    icon: ImageVector, contentDescription: String?, onClick: () -> Unit,
    size: Dp = 40.dp, filled: Boolean = false,
) {
    val bg = if (filled) Modifier.background(Fidelis.accent) else Modifier.background(Fidelis.glassStrong)
    Box(Modifier.size(size).clip(CircleShape).then(bg).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = Fidelis.ink, modifier = Modifier.size(size * 0.5f))
    }
}

/** Flat list row used for every song list in the app. */
@Composable
fun TrackRow(
    title: String, subtitle: String, onClick: () -> Unit,
    index: Int? = null, imageUrl: String? = null, highlighted: Boolean = false, trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        index?.let {
            Text("$it", color = if (highlighted) Fidelis.violet else Fidelis.inkFaint,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(30.dp))
        }
        imageUrl?.let {
            AsyncImage(it, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass),
                contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (highlighted) Fidelis.violet else Fidelis.ink,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Text(it, color = Fidelis.pink, style = MaterialTheme.typography.labelLarge) }
    }
}

/** Floating now-playing bar shared by Playlists and Mood. */
@Composable
fun MiniPlayer(
    snap: SpotifyRemote.Snapshot, art: Bitmap?, onOpen: () -> Unit, onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.fillMaxWidth().shadow(18.dp, shape).clip(shape)
            .background(Fidelis.bg1.copy(alpha = 0.94f)).border(1.dp, Fidelis.border, shape)
            .pressable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Fidelis.glass)) {
            art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(snap.title.ifBlank { "Nothing playing" }, color = Fidelis.ink,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(snap.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconCircle(
            if (snap.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            if (snap.isPaused) "Play" else "Pause", onToggle, size = 44.dp, filled = true,
        )
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
fun EmptyState(
    title: String, body: String, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                GradientButton(actionLabel, onAction)
            }
        }
    }
}

/** Pulsing placeholder shown while content loads. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.05f, 0.14f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(RoundedCornerShape(radius)).background(Color.White.copy(alpha = a)))
}
