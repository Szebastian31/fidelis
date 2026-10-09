package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Song row with cover, optional number/badge and a ⋮ menu. Used by every song list. */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    imageUrl: String? = null,
    index: Int? = null,
    highlighted: Boolean = false,
    badge: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick)
            .padding(start = 20.dp, end = if (onMore != null) 6.dp else 20.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        index?.let {
            Text("$it", color = if (highlighted) Fidelis.violet else Fidelis.inkFaint,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
        }
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass)) {
            imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = if (highlighted) Fidelis.violet else Fidelis.ink,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                badge?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Fidelis.ink, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Fidelis.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onMore?.let {
            Box(Modifier.size(44.dp).pressable(onClick = it), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreVert, "More", tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
            }
        }
    }
}
