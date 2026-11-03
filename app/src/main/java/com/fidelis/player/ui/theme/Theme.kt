package com.fidelis.player.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Looks shared with Kalendae, Augur and Consensus Weather. Each is a sky ([top] → [mid] → [bottom])
 * with three soft glows, plus Fidelis' accent gradient ([start] → [end]) and the [highlight] for
 * text and progress. [wash] is how much of the playing cover's colour flows into the sky.
 * Midnight is pure black for OLED screens.
 */
enum class Skin(
    val label: String,
    val top: Color, val mid: Color, val bottom: Color,
    val glow1: Color, val glow2: Color, val glow3: Color,
    val start: Color, val end: Color, val highlight: Color,
    val wash: Float = 1f,
) {
    Night("Night",
        Color(0xFF0A0F26), Color(0xFF161D47), Color(0xFF282C5E),
        Color(0x388FD6FF), Color(0x30B49CFF), Color(0x26E4657F),
        Color(0xFF8B5CC7), Color(0xFFE4657F), Color(0xFFB49CFF)),
    Dawn("Dawn",
        Color(0xFF1E1240), Color(0xFF5A3477), Color(0xFFC0697E),
        Color(0x4DFFC08A), Color(0x3DFF9FB4), Color(0x33A9C8FF),
        Color(0xFFFF9E7A), Color(0xFFE45F9A), Color(0xFFFFD2B5), wash = 0.8f),
    Aurora("Aurora",
        Color(0xFF020F14), Color(0xFF06292E), Color(0xFF0C4744),
        Color(0x4046F0AA), Color(0x388C6EFF), Color(0x2A3CC8FF),
        Color(0xFF22C997), Color(0xFF5BB8F0), Color(0xFF8AF2CF), wash = 0.9f),
    Ocean("Ocean",
        Color(0xFF020E1C), Color(0xFF062B4C), Color(0xFF0A587A),
        Color(0x405AD2FF), Color(0x305078FF), Color(0x24FFD296),
        Color(0xFF3A7BFF), Color(0xFF4FD0FF), Color(0xFFA6DCFF)),
    Midnight("Midnight",
        Color(0xFF000000), Color(0xFF04050A), Color(0xFF0A0C16),
        Color(0x30826EFF), Color(0x1E50A0FF), Color(0x1AFF78AA),
        Color(0xFF8B5CC7), Color(0xFFE4657F), Color(0xFFB49CFF), wash = 0.45f),
}

@Composable
fun FidelisTheme(skin: Skin, content: @Composable () -> Unit) {
    // Push the look into the shared palette before anything draws.
    remember(skin) { Fidelis.applySkin(skin); skin }
    val context = LocalContext.current
    remember { Ambience.load(context) }
    // The cover's colour flows in once a skip has settled, and out when nothing plays.
    val target = Ambience.tint.takeIf { Ambience.follow }
    LaunchedEffect(target) {
        if (target != null) delay(600)
        val fromC = Fidelis.moodColor; val fromA = Fidelis.moodAmount
        val toC = target ?: fromC
        val startC = if (fromA < 0.01f) toC else fromC
        val toA = if (target != null) 1f else 0f
        animate(0f, 1f, animationSpec = tween(1200, easing = FastOutSlowInEasing)) { v, _ ->
            Fidelis.setMood(lerp(startC, toC, v), fromA + (toA - fromA) * v)
        }
    }
    val scheme = darkColorScheme(
        primary = skin.highlight, onPrimary = Color.White,
        secondary = skin.end, onSecondary = Color.White,
        background = Fidelis.bg0, onBackground = Color.White,
        surface = Fidelis.bg1, onSurface = Color. White,
        surfaceVariant = Fidelis.bg2, onSurfaceVariant = Fidelis.inkMute,
    )
    MaterialTheme(colorScheme = scheme, typography = FidelisType, content = content)
}
