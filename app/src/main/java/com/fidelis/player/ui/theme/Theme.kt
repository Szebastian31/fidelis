package com.fidelis.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics. Color

/**
 * Colour themes. All share the midnight background; each sets the accent gradient
 * ([start] → [end]) and the [highlight] used for text, sliders and progress.
 */
enum class Skin(val label: String, val start: Color, val end: Color, val highlight: Color) {
    Midnight("Midnight", Color(0xFF8B5CC7), Color(0xFFE4657F), Color(0xFFB49CFF)),
    Aurora("Aurora", Color(0xFF1F9D8B), Color(0xFF5BB8F0), Color(0xFF7FE0D0)),
    Sunset("Sunset", Color(0xFFFF7A45), Color(0xFFE4507F), Color(0xFFFFB38A)),
    Ocean("Ocean", Color(0xFF3A5FD8), Color(0xFF7CC4F5), Color(0xFF9CC8FF)),
    Rose("Rose", Color(0xFFC2367E), Color(0xFFFF8E8E), Color(0xFFFFA8C8)),
}

@Composable
fun FidelisTheme(skin: Skin, content: @Composable () -> Unit) {
    // Push the theme's accents into the shared palette before anything draws.
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
