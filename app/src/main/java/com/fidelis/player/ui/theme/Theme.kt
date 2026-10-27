package com.fidelis.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Looks shared with Kalendae and Augur. Each is a sky ([top] → [mid] → [bottom]) with three soft
 * glows, plus Fidelis' accent gradient ([start] → [end]) and the [highlight] for text and progress.
 * Midnight is pure black for OLED screens.
 */
enum class Skin(
    val label: String,
    val top: Color, val mid: Color, val bottom: Color,
    val glow1: Color, val glow2: Color, val glow3: Color,
    val start: Color, val end: Color, val highlight: Color,
) {
    Night("Night",
        Color(0xFF070D26), Color(0xFF14215A), Color(0xFF2B3D7E),
        Color(0x428FD6FF), Color(0x38B9A8FF), Color(0x2EFF9FB4),
        Color(0xFF8F7CFF), Color(0xFFFF8FB1), Color(0xFFC3B5FF)),
    Dawn("Dawn",
        Color(0xFF2A1B6A), Color(0xFF77479A), Color(0xFFE0828E),
        Color(0x5CFFCD96), Color(0x47FF9FB4), Color(0x3DA9C8FF),
        Color(0xFFFF9E7A), Color(0xFFE45F9A), Color(0xFFFFD2B5)),
    Aurora("Aurora",
        Color(0xFF03141A), Color(0xFF08343A), Color(0xFF0F5552),
        Color(0x4746F0AA), Color(0x428C6EFF), Color(0x2E3CC8FF),
        Color(0xFF22C997), Color(0xFF5BB8F0), Color(0xFF8AF2CF)),
    Ocean("Ocean",
        Color(0xFF021222), Color(0xFF06345A), Color(0xFF0B6A8E),
        Color(0x475AD2FF), Color(0x385078FF), Color(0x29FFD296),
        Color(0xFF3A7BFF), Color(0xFF4FD0FF), Color(0xFFA6DCFF)),
    Midnight("Midnight",
        Color(0xFF000000), Color(0xFF04050A), Color(0xFF0A0C16),
        Color(0x38826EFF), Color(0x2450A0FF), Color(0x1FFF78AA),
        Color(0xFF8B5CC7), Color(0xFFE4657F), Color(0xFFB49CFF)),
}

@Composable
fun FidelisTheme(skin: Skin, content: @Composable () -> Unit) {
    // Push the look into the shared palette before anything draws.
    remember(skin) { Fidelis.applySkin(skin); skin }
    val scheme = darkColorScheme(
        primary = skin.highlight, onPrimary = Color.White,
        secondary = skin.end, onSecondary = Color.White,
        background = Fidelis.bg0, onBackground = Color.White,
        surface = Fidelis.bg1, onSurface = Color.White,
        surfaceVariant = Fidelis.bg2, onSurfaceVariant = Fidelis.inkMute,
    )
    MaterialTheme(colorScheme = scheme, typography = FidelisType, content = content)
}
