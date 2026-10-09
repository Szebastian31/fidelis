package com.fidelis.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fidelis.player.R

private val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

val FidelisType = Typography(
    displaySmall = style(FontWeight.ExtraBold, 36, 42, -1.0),
    headlineLarge = style(FontWeight.ExtraBold, 32, 38, -0.8),
    headlineMedium = style(FontWeight.ExtraBold, 30, 36, -0.7),
    headlineSmall = style(FontWeight.Bold, 24, 30, -0.4),
    titleLarge = style(FontWeight.Bold, 20, 26, -0.2),
    titleMedium = style(FontWeight.Bold, 16, 22),
    titleSmall = style(FontWeight.SemiBold, 15, 20),
    bodyLarge = style(FontWeight.Medium, 16, 24),
    bodyMedium = style(FontWeight.Medium, 14, 20),
    bodySmall = style(FontWeight.Medium, 12, 17, 0.1),
    labelLarge = style(FontWeight.Bold, 14, 18, 0.1),
    labelMedium = style(FontWeight.Bold, 11, 14, 1.2),
    labelSmall = style(FontWeight.SemiBold, 11, 14, 0.3),
)
