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
    Ember("Ember",
        Color(0xFF0D0705), Color(0xFF26110C), Color(0xFF451C12),
        Color(0x52FF9F4A), Color(0x40FF5A5F), Color(0x2EFFD27A),
        Color(0xFFFF7A3D), Color(0xFFE0436B), Color(0xFFFFC08A), wash = 0.85f),
    Graphite("Graphite",
        Color(0xFF0A0B0E), Color(0xFF16181E), Color(0xFF252932),
        Color(0x33C8D4EC), Color(0x2696AACC), Color(0x1FB4A0DC),
        Color(0xFF55648A), Color(0xFF8B98B8), Color(0xFFDDE3EE)),
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
