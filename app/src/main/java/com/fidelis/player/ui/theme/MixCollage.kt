package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Album-cover collage for a mix. Fills [modifier]'s box; clip it yourself. */
@Composable
fun MixCollage(covers: List<String>, modifier: Modifier = Modifier) {
    when {
        covers.size >= 4 -> Column(modifier) {
            Row(Modifier.weight(1f)) {
                AsyncImage(covers[0], null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                AsyncImage(covers[1], null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
            Row(Modifier.weight(1f)) {
                AsyncImage(covers[2], null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                AsyncImage(covers[3], null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
        }
        covers.isNotEmpty() -> AsyncImage(covers[0], null, modifier, contentScale = ContentScale.Crop)
        else -> Box(modifier.background(Fidelis.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Fidelis.ink, modifier = Modifier.size(32.dp))
        }
    }
}
