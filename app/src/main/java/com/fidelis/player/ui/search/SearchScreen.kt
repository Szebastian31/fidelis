package com.fidelis.player.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3. Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.PlaylistItem
import com.fidelis.player.integration.spotify.SpotifyLink
import com.fidelis.player.integration.spotify.openInSpotify
import com.fidelis.player.ui.theme.*

/** One bar for songs, artists and albums; results come back in three sections. */
@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel(),
) {
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var sheetFor by remember { mutableStateOf<PlaylistTrack?>(null) }
    var listFor by remember { mutableStateOf<PlaylistItem?>(null) }
    val r = vm.results
    val link = vm.link
    val pinned by vm.pinned.collectAsState()
    val toast = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    val pad = Modifier.padding(horizontal = 20.dp)

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
                item { Box(pad) { ScreenHeader("Search", eyebrow = "Spotify") } }
                item {
                    SearchField(vm.query, vm::onQuery, onSubmit = { vm.remember(); keyboard?.hide() }, pad)
                }
                when {
                    vm.query.isBlank() -> {
                        if (vm.recent.isEmpty()) {
                            item {
                                Text("Find songs, artists, albums and playlists, or paste a Spotify profile or playlist link.", color = Fidelis.inkMute,
                                    style = MaterialTheme.typography.bodyMedium, modifier = pad.padding(top = 20.dp))
                            }
                        } else {
                            item {
                                Row(pad, verticalAlignment = Alignment.Bottom) {
                                    SectionLabel("Recent searches", Modifier.weight(1f))
                                    Text("Clear", color = Fidelis.violet, style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(bottom = 10.dp).pressable(onClick = vm::clearRecent))
                                }
                            }
                            items(vm.recent, key = { "r-$it" }) { q ->
                                Row(Modifier.fillMaxWidth().pressable { vm.useRecent(q) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.History, null, tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Text(q, color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    link != null -> item {
                        val on = pinned.any { it.uri == link.uri }
                        LinkCard(link, vm.linked, on, pad,
                            onPlay = vm::playLink,
                            onPin = { vm.pinLink(); toast(if (on) "Unpinned" else "Pinned to Your playlists") },
                            onOpen = { context.openInSpotify(link.kind, link.id) })
                    }
                    r == null -> item {
                        Column(pad.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(6) { SkeletonBox(Modifier.fillMaxWidth().height(56.dp)) }
                        }
                    }
                    r.isEmpty && !vm.loading -> item {
                        EmptyState("No results", "Try another spelling, or search for an artist or album.",
                            pad.padding(top = 24.dp))
                    }
                    else -> {
                        if (r.artists.isNotEmpty()) {
                            item { SectionLabel("Artists", pad) }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(r.artists, key = { it.id }) { a ->
                                        ArtistBubble(a.name, a.imageUrl) { vm.remember(); onOpenArtist(a.id) }
                                    }
                                }
                            }
                        }
                        if (r.albums.isNotEmpty()) {
                            item { SectionLabel("Albums", pad) }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(r.albums, key = { it.id }) { a ->
                                        AlbumTile(a.name, "${a.artist} · ${a.label}", a.imageUrl) {
                                            vm.remember(); onOpenAlbum(a.id)
                                        }
                                    }
                                }
                            }
                        }
                        if (r.tracks.isNotEmpty()) {
                            item { SectionLabel("Songs", pad) }
                            itemsIndexed(r.tracks, key = { _, t -> t.uri }) { i, t ->
                                SongRow(title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                                    onClick = { vm.playSong(i) }, onMore = { sheetFor = t })
                            }
                            if (vm.canLoadMore) item {
                                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                    SoftButton(if (vm.loadingMore) "Loading…" else "More songs", vm::loadMore,
                                        enabled = !vm.loadingMore)
                                }
                            }
                        }
                        if (r.playlists.isNotEmpty()) {
                            item { SectionLabel("Playlists", pad) }
                            items(r.playlists, key = { "p-${it.id}" }) { p ->
                                SongRow(title = p.name, subtitle = p.label, imageUrl = p.imageUrl,
                                    onClick = { vm.playPlaylist(p); toast("Playing ${p.name}") },
                                    onMore = { listFor = p })
                            }
                            if (vm.canMorePlaylists) item {
                                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                    SoftButton(if (vm.loadingPlaylists) "Loading\u2026" else "More playlists", vm::morePlaylists,
                                        enabled = !vm.loadingPlaylists)
                                }
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

    sheetFor?.let { t ->
        TrackSheet(
            uri = t.uri, title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
            actions = listOf(
                SheetAction("Play now", Icons.Rounded.PlayArrow) {
                    vm.playSong(vm.results?.tracks?.indexOf(t) ?: -1)
                },
                SheetAction("Play next", Icons.Rounded.SkipNext) {
                    vm.playNext(t); Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                },
                SheetAction("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) {
                    vm.addToQueue(t); Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                },
            ),
            onDismiss = { sheetFor = null },
        )
    }

    listFor?.let { p ->
        val on = pinned.any { it.uri == p.uri }
        TrackSheet(
            title = p.name, subtitle = p.label, imageUrl = p.imageUrl,
            actions = buildList {
                add(SheetAction("Play", Icons.Rounded.PlayArrow) { vm.playPlaylist(p) })
                add(SheetAction(if (on) "Unpin" else "Pin to Your playlists", Icons.Rounded.PushPin) {
                    vm.togglePin(p); toast(if (on) "Unpinned" else "Pinned to Your playlists")
                })
                if (p.ownerId.isNotBlank()) {
                    add(SheetAction("See ${p.owner.ifBlank { "the owner" }}'s profile in Spotify", Icons.Rounded.Person) {
                        context.openInSpotify("user", p.ownerId)
                    })
                }
                add(SheetAction("Open in Spotify", Icons.AutoMirrored.Rounded.OpenInNew) {
                    context.openInSpotify("playlist", p.id)
                })
            },
            onDismiss = { listFor = null },
        )
    }
}

/**
 * A pasted link. Spotify stopped letting apps look up people in 2026, so profiles open in the
 * Spotify app; a playlist link can be played or pinned here.
 */
@Composable
private fun LinkCard(
    link: SpotifyLink, playlist: PlaylistItem?, pinned: Boolean, modifier: Modifier,
    onPlay: () -> Unit, onPin: () -> Unit, onOpen: () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (link.isProfile) {
            SectionLabel("Spotify profile")
            Text("Spotify doesn't let other apps look up people or their playlists any more, so profiles open in " +
                "the Spotify app. Share one of their playlists from there and paste the link here to play or pin it.",
                color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium)
            SoftButton("Open profile in Spotify", onOpen)
        } else {
            SectionLabel("Playlist link")
            Text(playlist?.name ?: "Spotify playlist", color = Fidelis.ink, style = MaterialTheme.typography.titleMedium)
            playlist?.label?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Play", onPlay)
                SoftButton(if (pinned) "Unpin" else "Pin to Your playlists", onPin)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Fidelis.glassStrong)
            .border(1.dp, Fidelis.line, CircleShape).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Fidelis.inkMute, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Songs, albums, playlists or a link", color = Fidelis.inkFaint, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fidelis.ink),
                cursorBrush = SolidColor(Fidelis.violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(40.dp).pressable { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Clear", tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
            }
        }
    }
}
