package com.fidelis.player.ui.podcasts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text. KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics. Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.PODCAST_GENRES
import com.fidelis.player.integration.spotify.PodcastPick
import com.fidelis.player.integration.spotify.PodcastRow
import com.fidelis.player.integration.spotify.podKey
import com.fidelis.player.ui.radio.stationColor
import com.fidelis.player.ui.theme.*

@Composable
internal fun PodcastSearchField(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val focus = LocalFocusManager.current
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(50.dp).clip(shape)
            .background(Fidelis.glass).border(1.dp, Fidelis.border, shape).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons. Rounded.Search, null, tint = Fidelis.inkMute, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search any podcast", color = Fidelis.inkFaint, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                query, onQuery, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fidelis.ink),
                cursorBrush = Fidelis.accent,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
        }
        if (query.isNotEmpty()) IconCircle(Icons.Rounded.Close, "Clear", { onQuery(""); focus.clearFocus() }, size = 34.dp)
    }
}

/** Short messages, such as a show that isn't on Spotify. */
@Composable
internal fun PodcastNotice(notice: String?, onShown: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(notice) {
        if (notice != null) { Toast.makeText(ctx, notice, Toast.LENGTH_SHORT).show(); onShown() }
    }
}

/** Search results, with the shows you follow marked. */
internal fun LazyListScope.podcastResults(results: List<PodcastPick>?, followed: Set<String>, onOpen: (PodcastPick) -> Unit) {
    when {
        results == null -> items(5) {
            SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth().height(72.dp))
        }
        results.isEmpty() -> item {
            EmptyState("No podcasts found", "Try a show name, a host or a topic.", Modifier.padding(horizontal = 20.dp))
        }
        else -> items(results, key = { "q" + it.key }) { p -> ShowRow(p, podKey(p.name) in followed) { onOpen(p) } }
    }
}

/** Genre chips, your own genres first, then the rows picked for you. */
internal fun LazyListScope.podcastPicks(vibe: List<String>, rows: List<PodcastRow>, onGenre: (String) -> Unit, onOpen: (PodcastPick) -> Unit) {
    item(key = "genres") {
        Column {
            RowHeader(if (vibe.isEmpty()) "Browse" else "Your vibe",
                if (vibe.isEmpty()) "Top shows by genre" else vibe.take(2).joinToString(" · "))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vibe, key = { "v$it" }) { GenreChip(it, mine = true) { onGenre(it) } }
                items(PODCAST_GENRES.keys.filter { it !in vibe }, key = { "g$it" }) { GenreChip(it, mine = false) { onGenre(it) } }
            }
        }
    }
    rows.forEach { r ->
        item(key = "row" + r.key) {
            Column {
                RowHeader(r.eyebrow, r.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(r.shows, key = { it.key }) { p -> PickTile(p) { onOpen(p) } }
                }
            }
        }
    }
}

@Composable
private fun RowHeader(eyebrow: String, title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)) {
        Text(eyebrow.uppercase(), color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
        Text(title, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun GenreChip(text: String, mine: Boolean, onClick: () -> Unit) {
    val c = stationColor(text)
    val shape = RoundedCornerShape(50)
    val fill: Brush = if (mine) Brush.linearGradient(listOf(lerp(c, Color.White, 0.1f), lerp(c, Color.Black, 0.4f)))
        else SolidColor(Fidelis.glass)
    Box(Modifier.pressable(onClick = onClick).clip(shape).background(fill).border(1.dp, Fidelis.border, shape)
        .padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PickTile(p: PodcastPick, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.width(124.dp).pressable(onClick = onClick)) {
        AsyncImage(p.imageUrl, p.name, Modifier.size(124.dp).shadow(10.dp, shape).clip(shape).background(Fidelis.glass),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.height(8.dp))
        Text(p.name, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.by.isNotBlank()) Text(p.by, color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShowRow(p: PodcastPick, following: Boolean, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().pressable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(p.imageUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Fidelis.glass),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.by.isNotBlank()) Text(p.by, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (following) Text("Following", color = Fidelis.violet, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp))
    }
}
