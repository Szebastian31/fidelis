package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Round artist photo with the name under it. Shows the first letter when there's no photo. */
@Composable
fun ArtistBubble(name: String, imageUrl: String?, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Fidelis.glassStrong), contentAlignment = Alignment.Center) {
            if (imageUrl != null) AsyncImage(imageUrl, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(1).uppercase(), color = Fidelis.inkSoft, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Square album cover with title and a small subtitle ("Album · 2021"). */
@Composable
fun AlbumTile(title: String, subtitle: String, imageUrl: String?, onClick: () -> Unit) {
    Column(Modifier.width(136.dp).pressable(onClick = onClick)) {
        Box(Modifier.size(136.dp).clip(RoundedCornerShape(16.dp)).background(Fidelis.glassStrong)) {
            imageUrl?.let { AsyncImage(it, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
