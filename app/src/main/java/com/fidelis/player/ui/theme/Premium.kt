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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.SpotifyRemote
import kotlinx.coroutines.delay

/** Design tokens. Every screen pulls colour from here, never ad-hoc. Colours follow the look (Skin). */
object Fidelis {
    var bg0 by mutableStateOf(Skin.Night.top); private set
    var bg1 by mutableStateOf(lerp(Skin.Night.top, Skin.Night.mid, 0.55f)); private set
    var bg2 by mutableStateOf(Skin.Night.mid); private set
    var bg3 by mutableStateOf(Skin.Night.bottom); private set
    var glow1 by mutableStateOf(Skin.Night.glow1); private set
    var glow2 by mutableStateOf(Skin.Night.glow2); private set
    var glow3 by mutableStateOf(Skin.Night.glow3); private set
    var purple by mutableStateOf(Skin.Night.start); private set
    var pink by mutableStateOf(Skin.Night.end); private set
    var violet by mutableStateOf(Skin.Night.highlight); private set
    val orange = Color(0xFFFF9E5A); val sky = Color(0xFF7CC4F5)
    val ink = Color(0xFFFFFFFF); val inkSoft = Color(0xD9FFFFFF)
    val inkMute = Color(0x8CFFFFFF); val inkFaint = Color(0x52FFFFFF)
    val glass = Color(0x14FFFFFF); val glassStrong = Color(0x26FFFFFF); val line = Color(0x1FFFFFFF)
    val border = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF)))
    val skyGradient: Brush get() = Brush.verticalGradient(0f to bg0, 0.6f to bg2, 1f to bg3)
    val accent: Brush get() = Brush.linearGradient(listOf(purple, pink))

    fun applySkin(s: Skin) {
        bg0 = s.top; bg1 = lerp(s.top, s.mid, 0.55f); bg2 = s.mid; bg3 = s.bottom
        glow1 = s.glow1; glow2 = s.glow2; glow3 = s.glow3
        purple = s.start; pink = s.end; violet = s.highlight
    }
}

/** The family sky: a gradient with three soft glows, like the banners of the sibling apps. */
@Composable
fun FidelisBackground(content: @Composable () -> Unit) {
    val g1 = Fidelis.glow1; val g2 = Fidelis.glow2; val g3 = Fidelis.glow3
    Box(
        Modifier.fillMaxSize().background(Fidelis.skyGradient).drawBehind {
            val w = size.width; val h = size.height
            drawRect(Brush.radialGradient(listOf(g1, Color.Transparent), center = Offset(w * 0.14f, h * 0.06f), radius = w * 0.9f))
            drawRect(Brush.radialGradient(listOf(g2, Color.Transparent), center = Offset(w * 0.9f, h * 0.3f), radius = w * 0.8f))
            drawRect(Brush.radialGradient(listOf(g3, Color.Transparent), center = Offset(w * 0.4f, h * 1.04f), radius = w))
        }
    ) { content() }
}

/** A colour taken from the album art, made rich enough to tint surfaces. Null for grey covers. */
fun artTint(bmp: Bitmap): Color? = runCatching {
    val src = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
    val small = Bitmap.createScaledBitmap(src, 12, 12, true)
    var r = 0L; var g = 0L; var b = 0L
    for (x in 0 until 12) for (y in 0 until 12) {
        val p = small.getPixel(x, y)
        r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
    }
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r / 144).toInt(), (g / 144).toInt(), (b / 144).toInt(), hsv)
    if (hsv[1] < 0.12f) return@runCatching null
    hsv[1] = hsv[1].coerceIn(0.35f, 0.85f); hsv[2] = hsv[2].coerceIn(0.45f, 0.8f)
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrNull()

@Composable
fun rememberArtTint(art: Bitmap?): Color? = remember(art) { art?.let { artTint(it) } }

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
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(Fidelis.glass).border(1.dp, Fidelis.border, shape)) { content() }
}

@Composable
fun ScreenHeader(title: String, eyebrow: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(it.uppercase(), color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
            }
            Text(title, color = Fidelis.ink,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
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
        modifier.pressable(enabled, onClick).height(52.dp).alpha(if (enabled) 1f else 0.4f)
            .shadow(16.dp, CircleShape, ambientColor = Fidelis.purple, spotColor = Fidelis.purple)
            .clip(CircleShape).background(Fidelis.accent).padding(horizontal = 22.dp),
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
        modifier.pressable(enabled, onClick).height(52.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape)
            .background(Fidelis.glassStrong).border(1.dp, Fidelis.line, CircleShape).padding(horizontal = 22.dp),
        verticalAlignment = Alignment. CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, tint = Fidelis.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun FidelisChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) Modifier.background(Fidelis.accent)
             else Modifier.background(Fidelis.glass).border(1.dp, Fidelis.line, CircleShape)
    Box(modifier.pressable(onClick = onClick).clip(CircleShape).then(bg).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(label, color = if (selected) Fidelis.ink else Fidelis.inkSoft,
            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconCircle(
    icon: ImageVector, contentDescription: String?, onClick: () -> Unit,
    size: Dp = 40.dp, filled: Boolean = false,
) {
    val bg = if (filled) Modifier.background(Fidelis.accent)
             else Modifier.background(Fidelis.glassStrong).border(1.dp, Fidelis.line, CircleShape)
    Box(Modifier.pressable(onClick = onClick).size(size).clip(CircleShape).then(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = Fidelis.ink, modifier = Modifier.size(size * 0.5f))
    }
}

/** Flat list row (older lists; new ones use SongRow). */
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
            AsyncImage(it, null, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Fidelis.glass),
                contentScale = ContentScale. Crop)
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

/** Floating now-playing pill, tinted by the album art, with a thin live progress line. */
@Composable
fun MiniPlayerPill(
    snap: SpotifyRemote.Snapshot, art: Bitmap?, onOpen: () -> Unit, onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val tint = rememberArtTint(art)
    val glow = tint ?: Fidelis.purple
    val fill = lerp(Fidelis.bg1, tint ?: Fidelis.bg1, 0.3f)
    var position by remember(snap) { mutableLongStateOf(snap.livePosition()) }
    LaunchedEffect(snap) {
        if (!snap.isPaused) while (true) { delay(500); position = snap.livePosition() }
    }
    val progress = if (snap.durationMs > 0) (position.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier.fillMaxWidth().pressable(onClick = onOpen)
            .shadow(22.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape).background(fill.copy(alpha = 0.97f)).border(1.dp, Fidelis.border, shape),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(Fidelis.glass)) {
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
        Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 3.dp)
                .fillMaxWidth().height(2.dp).clip(CircleShape).background(Fidelis.glass),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Fidelis.accent))
        }
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

/** True when the app shows one shared mini player above the tab bar; screens then skip their own. */
val LocalAppMini = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun MiniPlayer(
    snap: SpotifyRemote.Snapshot, art: Bitmap?, onOpen: () -> Unit, onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalAppMini.current) return
    MiniPlayerPill(snap, art, onOpen, onToggle, modifier)
}
