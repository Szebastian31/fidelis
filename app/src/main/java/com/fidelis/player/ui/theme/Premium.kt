package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime. Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The Consensus-Weather-derived premium palette. */
object Fidelis {
    val bg0 = Color(0xFF0A0F26); val bg1 = Color(0xFF161D47); val bg2 = Color(0xFF282C5E)
    val violet = Color(0xFFB49CFF); val pink = Color(0xFFE4657F); val purple = Color(0xFF8B5CC7)
    val orange = Color(0xFFFF9E5A); val sky = Color(0xFF7CC4F5)
    val ink = Color(0xFFFFFFFF); val inkSoft = Color(0xD6FFFFFF); val inkMute = Color(0x9EFFFFFF)
    val glass = Color(0x570E1226); val line = Color(0x29FFFFFF)
    val skyGradient = Brush.verticalGradient(listOf(bg0, bg1, bg2))
    val accent = Brush.linearGradient(listOf(purple, pink))
}

@Composable
fun FidelisBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Fidelis.skyGradient)) { content() }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = Fidelis.glass, contentColor = Fidelis.ink,
        shape = RoundedCornerShape(28.dp)) {
        Box(Modifier.border(1.dp, Fidelis.line, RoundedCornerShape(28.dp))) { content() }
    }
}
