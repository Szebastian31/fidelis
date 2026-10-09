package com.fidelis.player.ui.search

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.ui.theme.*

@Composable
fun SearchScreen(onOpenPlayer: () -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val playing = snap?.takeIf { it.title.isNotBlank() }
    var menuFor by remember { mutableStateOf<PlaylistTrack?>(null) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (playing != null) 100.dp else 24.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ScreenHeader(title = "Search", eyebrow = "All of Spotify")
                        OutlinedTextField(
                            value = vm.query,
                            onValueChange = vm::onQuery,
                            placeholder = { Text("Songs or artists", color = Fidelis.inkFaint) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Fidelis.inkMute) },
                            trailingIcon = {
                                if (vm.query.isNotEmpty()) {
                                    Box(Modifier.size(40.dp).clip(CircleShape).pressable { vm.onQuery("") },
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Close, "Clear", tint = Fidelis.inkMute)
                                    }
                                }
                            },
                            singleLine = true,
                            enabled = connected,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.submit(); focus.clearFocus() }),
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Fidelis.ink, unfocusedTextColor = Fidelis.ink,
                                focusedContainerColor = Fidelis.glass, unfocusedContainerColor = Fidelis.glass,
                                disabledContainerColor = Fidelis.glass,
                                focusedBorderColor = Fidelis.violet, unfocusedBorderColor = Fidelis.line,
                                disabledBorderColor = Fidelis.line, cursorColor = Fidelis.violet,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (vm.loading && vm.results.isEmpty()) {
                    item {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).clip(CircleShape),
                            color = Fidelis.violet, trackColor = Fidelis.glass,
                        )
                    }
                }

                when {
                    !connected -> item {
                        EmptyState(title = "Connect Spotify", body = "Connect in Profile to search all of Spotify.",
                            modifier = Modifier.padding(20.dp))
                    }

                    vm.query.isBlank() -> {
                        if (vm.recent.isNotEmpty()) {
                            item {
                                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SectionLabel("Recent searches", Modifier.weight(1f))
                                    Text("Clear", color = Fidelis.violet, style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(top = 14.dp).pressable(onClick = vm::clearRecent))
                                }
                            }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(vm.recent) { q -> FidelisChip(q, false, { vm.useRecent(q) }) }
                                }
                            }
                        } else {
                            item {
                                Text("Find any song on Spotify. Tap to play it now, or use ⋮ to queue it.",
                                    color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp))
                            }
                        }
                    }

                    !vm.loading && vm.results.isEmpty() -> item {
                        EmptyState(title = "No results", body = "Nothing matched “${vm.query}”. Try fewer words.",
                            modifier = Modifier.padding(20.dp))
                    }

                    else -> {
                        item { Spacer(Modifier.height(12.dp)) }
                        items(vm.results, key = { it.uri }) { t ->
                            SongRow(
                                title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                                highlighted = t.uri == snap?.uri,
                                onClick = { focus.clearFocus(); vm.playNow(t) },
                                onMore = { focus.clearFocus(); menuFor = t },
                            )
                        }
                        if (vm.hasMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    SoftButton(if (vm.loading) "Loading…" else "Show more", vm::loadMore,
                                        enabled = !vm.loading)
                                }
                            }
                        }
                    }
                }
            }

            playing?.let {
                MiniPlayer(
                    snap = it, art = art, onOpen = onOpenPlayer, onToggle = vm::togglePlay,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }

    menuFor?.let { t ->
        TrackSheet(
            title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
            actions = listOf(
                SheetAction("Play now", Icons.Rounded.PlayArrow) { vm.playNow(t) },
                SheetAction("Play next", Icons. Rounded.SkipNext) { vm.playNext(t); toast("Plays next") },
                SheetAction("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { vm.addToQueue(t); toast("Added to queue") },
            ),
            onDismiss = { menuFor = null },
        )
    }
}
