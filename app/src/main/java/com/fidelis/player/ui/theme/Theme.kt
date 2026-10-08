package com.fidelis.player.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics. Color

/**
 * User-selectable skins. Each maps to a Material 3 [ColorScheme] so the whole app
 * re-themes instantly when the user picks one in Profile.
 */
enum class Skin(val label: String, val isDark: Boolean) {
    Daylight("Daylight", false),
    Vinyl("Vinyl", false),
    Mint("Mint", false),
    Nebula("Nebula", false),
    Midnight("Midnight", true);

    fun scheme(): ColorScheme = when (this) {
        Daylight -> lightColorScheme(
            primary = Color(0xFFFF5E3A), background = Color(0xFFF3ECDF),
            surface = Color(0xFFFBF7EF), onBackground = Color(0xFF1C1A17), onSurface = Color(0xFF1C1A17),
        )
        Vinyl -> lightColorScheme(
            primary = Color(0xFFC65A2E), background = Color(0xFFECE0CC),
            surface = Color(0xFFF6EEDD), onBackground = Color(0xFF2A2016), onSurface = Color(0xFF2A2016),
        )
        Mint -> lightColorScheme(
            primary = Color(0xFF11A37A), background = Color(0xFFE6F2EC),
            surface = Color(0xFFF5FBF7), onBackground = Color(0xFF15241D), onSurface = Color(0xFF15241D),
        )
        Nebula -> lightColorScheme(
            primary = Color(0xFF7A5CFF), background = Color(0xFFECE6F7),
            surface = Color(0xFFF8F5FE), onBackground = Color(0xFF221A33), onSurface = Color(0xFF221A33),
        )
        Midnight -> darkColorScheme(
            primary = Color(0xFF39D7C4), background = Color(0xFF16140F),
            surface = Color(0xFF211E18), onBackground = Color(0xFFF3ECDF), onSurface = Color(0xFFF3ECDF),
        )
    }
}

@Composable
fun FidelisTheme(skin: Skin, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = skin.scheme(), typography = FidelisType, content = content)
}
