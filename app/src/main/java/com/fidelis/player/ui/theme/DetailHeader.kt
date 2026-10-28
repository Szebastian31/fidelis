package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Shared header for song-list pages (playlist, Liked Songs, mix): blurred cover glow,
 * large cover, eyebrow, bold title, a meta line and a row of buttons.
 */
@Composable
fun DetailHeader(
    eyebrow: String,
    title: String,
    meta: String?,
    backdropUrl: String?,
    onBack: () -> Unit,
    cover: @Composable BoxScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        backdropUrl?.let {
            AsyncImage(it, null,
                Modifier.matchParentSize().blur(80.dp)
                    .graphicsLayer { alpha = 0.6f; compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0.3f to Color. Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn)
                    },
                contentScale = ContentScale.Crop)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.weight(1f))
                trailing()
            }
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier.size(232.dp)
                    .shadow(30.dp, shape, ambientColor = Fidelis.purple, spotColor = Fidelis.purple)
                    .clip(shape).background(Fidelis.glassStrong),
                contentAlignment = Alignment.Center,
                content = cover,
            )
            Spacer(Modifier.height(18.dp))
            Text(eyebrow.uppercase(), color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(title, color = Fidelis.ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
            meta?.let {
                Text(it, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically, content = actions)
            Spacer(Modifier.height(14.dp))
        }
    }
}
