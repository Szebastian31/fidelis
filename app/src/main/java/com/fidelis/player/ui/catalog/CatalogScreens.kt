package com.fidelis.player.ui.catalog

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3. MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.AlbumItem
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.delay

/** Artist: photo, Play / Shuffle / Artist radio, albums, singles & EPs, then songs. */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: ArtistViewModel = hiltViewModel(),
) {
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val context = LocalContext.current
    var sheetFor by remember { mutableStateOf<PlaylistTrack?>(null) }
    val a = vm.artist
    val pad = Modifier.padding(horizontal = 20.dp)
    val hasSongs = vm.songs.isNotEmpty()

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
                item { TopBar(onBack) }
                item {
                    Column(pad.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(176.dp)
                                .shadow(30.dp, CircleShape, ambientColor = Fidelis.purple, spotColor = Fidelis.purple)
                                .clip(CircleShape).background(Fidelis.glassStrong),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                a?.imageUrl != null -> AsyncImage(a.imageUrl, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                a != null -> Text(a.name.take(1).uppercase(), color = Fidelis.inkSoft,
                                    style = MaterialTheme.typography.displaySmall)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("ARTIST", color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
                        Text(a?.name.orEmpty(), color = Fidelis.ink, textAlign = TextAlign.Center, maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientButton("Play", { vm.play() }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = hasSongs)
                            SoftButton("Shuffle", { vm.shuffle() }, Modifier.weight(1f), icon = Icons.Rounded.Shuffle, enabled = hasSongs)
                        }
                        Spacer(Modifier.height(10.dp))
                        SoftButton(
                            if (vm.buildingRadio) "Building radio…" else "Artist radio",
                            onClick = {
                                vm.startRadio { ok ->
                                    if (ok) onOpenPlayer()
                                    else Toast.makeText(context, "Couldn't build a radio for this artist", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Radio,
                            enabled = hasSongs && !vm.buildingRadio,
                        )
                        if (a != null) {
                            Text("Mostly ${a.name}, with similar songs mixed in", color = Fidelis.inkFaint,
                                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                when {
                    vm.failed -> item {
                        EmptyState("Couldn't load this artist", "Check your connection and try again.",
                            pad.padding(top = 24.dp), "Try again", vm::load)
                    }
                    vm.loading -> item { Skeletons(pad) }
                    else -> {
                        if (vm.albums.isNotEmpty()) {
                            item { SectionLabel("Albums", pad) }
                            item { ReleaseRow(vm.albums, onOpenAlbum) }
                        }
                        if (vm.singles.isNotEmpty()) {
                            item { SectionLabel("Singles & EPs", pad) }
                            item { ReleaseRow(vm.singles, onOpenAlbum) }
                        }
                        if (hasSongs) {
                            item { SectionLabel(if (vm.byPopularity) "Popular on Last.fm" else "Songs", pad) }
                            itemsIndexed(vm.songs, key = { _, t -> t.uri }) { i, t ->
                                SongRow(title = t.title, subtitle = vm.subtitle(t), onClick = { vm.play(i) },
                                    onMore = { sheetFor = t }, imageUrl = t.imageUrl, highlighted = snap?.uri == t.uri)
                            }
                        }
                    }
                }
            }
            snap?.takeIf { it.title.isNotBlank() }?.let { s ->
                MiniPlayer(s, art, onOpen = onOpenPlayer, onToggle = vm::togglePlay,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
    sheetFor?.let { t -> SongSheet(t, vm::playNext, vm::addToQueue) { sheetFor = null } }
}

/** Album: cover, title, artist (tap to open), Play in order / Shuffle / Add all, numbered songs. */
@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: AlbumViewModel = hiltViewModel(),
) {
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val context = LocalContext.current
    var sheetFor by remember { mutableStateOf<PlaylistTrack?>(null) }
    val p = vm.page
    val pad = Modifier.padding(horizontal = 20.dp)
    val songs = p?.tracks.orEmpty()
    // Checked while the album is open, so the note goes away once crossfade is turned off.
    LaunchedEffect(Unit) { while (true) { vm.checkCrossfade(); delay(4_000) } }

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
                item { TopBar(onBack) }
                item {
                    Column(pad.fillMaxWidth(), horizontalAlignment = Alignment. CenterHorizontally) {
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            Modifier.size(232.dp)
                                .shadow(30.dp, shape, ambientColor = Fidelis.purple, spotColor = Fidelis.purple)
                                .clip(shape).background(Fidelis.glassStrong),
                        ) {
                            p?.album?.imageUrl?.let { AsyncImage(it, p.album.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(p?.album?.name.orEmpty(), color = Fidelis.ink, textAlign = TextAlign.Center, maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
                        p?.album?.let { al ->
                            Spacer(Modifier.height(4.dp))
                            Text(al.artist, color = Fidelis.violet, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.pressable(enabled = al.artistId != null) { al.artistId?.let(onOpenArtist) })
                            Text("${al.label} · ${songs.size} songs", color = Fidelis.inkMute,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GradientButton("Play", { vm.play() }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow,
                                enabled = songs.isNotEmpty())
                            SoftButton("Shuffle", { vm.shuffle() }, Modifier.weight(1f), icon = Icons.Rounded.Shuffle,
                                enabled = songs.isNotEmpty())
                            IconCircle(Icons.AutoMirrored.Rounded.QueueMusic, "Add all to queue", onClick = {
                                if (songs.isNotEmpty()) {
                                    vm.addAll(); Toast.makeText(context, "Added ${songs.size} songs to queue", Toast.LENGTH_SHORT).show()
                                }
                            }, size = 52.dp)
                        }
                        if (vm.crossfadeOn) CrossfadeNote()
                        Spacer(Modifier.height(8.dp))
                    }
                }
                when {
                    vm.failed -> item {
                        EmptyState("Couldn't load this album", "Check your connection and try again.",
                            pad.padding(top = 24.dp), "Try again", vm::load)
                    }
                    vm.loading -> item { Skeletons(pad) }
                    else -> itemsIndexed(songs, key = { _, t -> t.uri }) { i, t ->
                        SongRow(title = t.title, subtitle = t.artist, onClick = { vm.play(i) }, onMore = { sheetFor = t },
                            imageUrl = t.imageUrl, index = i + 1, highlighted = snap?.uri == t.uri)
                    }
                }
            }
            snap?.takeIf { it.title.isNotBlank() }?.let { s ->
                MiniPlayer(s, art, onOpen = onOpenPlayer, onToggle = vm::togglePlay,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
    sheetFor?.let { t -> SongSheet(t, vm::playNext, vm::addToQueue) { sheetFor = null } }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
    }
}

@Composable
private fun ReleaseRow(releases: List<AlbumItem>, onOpen: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(releases, key = { it.id }) { r -> AlbumTile(r.name, r.label, r.imageUrl) { onOpen(r.id) } }
    }
}

@Composable
private fun Skeletons(modifier: Modifier) {
    Column(modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { SkeletonBox(Modifier.fillMaxWidth().height(56.dp)) }
    }
}

@Composable
private fun SongSheet(
    t: PlaylistTrack, playNext: (PlaylistTrack) -> Unit, addToQueue: (PlaylistTrack) -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackSheet(
        uri = t.uri, title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
        actions = listOf(
            SheetAction("Play next", Icons.Rounded.SkipNext) {
                playNext(t); Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
            },
            SheetAction("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) {
                addToQueue(t); Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            },
        ),
        onDismiss = onDismiss,
    )
}

/** Spotify only lets you change crossfade in its own settings, so this points the way there. */
@Composable
private fun CrossfadeNote() {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Fidelis.glass)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Crossfade is on", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
            Text("Albums made to flow song to song sound best without it. Spotify › Settings › Playback › Crossfade.",
                color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(10.dp))
        SoftButton("Open Spotify", {
            context.packageManager.getLaunchIntentForPackage("com.spotify.music")?.let(context::startActivity)
        })
    }
}
