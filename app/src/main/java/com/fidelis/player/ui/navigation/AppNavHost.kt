package com.fidelis.player.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fidelis.player.integration.spotify.OpenedItems
import com.fidelis.player.ui.playlists.SpotifyPageScreen
import com.fidelis.player.ui.podcasts.PodcastsScreen
import com.fidelis.player.ui.podcasts.ShowScreen
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose. NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.ui.catalog.AlbumScreen
import com.fidelis.player.ui.catalog.ArtistScreen
import com.fidelis.player.ui.mood.MixDetailScreen
import com.fidelis.player.ui.mood.MoodScreen
import com.fidelis.player.ui.player.NowPlayingScreen
import com.fidelis.player.ui.playlists.PlaylistDetailScreen
import com.fidelis.player.ui.playlists.PlaylistsScreen
import com.fidelis.player.ui.profile.ProfileScreen
import com.fidelis.player.ui.search.SearchScreen
import com.fidelis.player.ui.theme.ChromeInsets
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.Skin
import com.fidelis.player.ui.theme.SwipeMini
import com.fidelis.player.ui.theme.Ambience
import com.fidelis.player.ui.theme.artTint
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fidelis.player.ui.theme.pressable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Playlists("playlists", "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    Search("search", "Search", Icons.Rounded.Search),
    Podcasts("podcasts", "Podcasts", Icons.Rounded.Podcasts),
    Mood("mood", "Mood", Icons.Rounded.AutoAwesome),
    Profile("profile", "Profile", Icons.Rounded.Person),
}

const val PLAYER = "nowplaying"
private const val DETAIL = "playlist/{playlistId}"
private const val MIX = "mix/{mixId}"
private const val ARTIST = "artist/{artistId}"
private const val ALBUM = "album/{albumId}"
private const val SPOTIFY = "spotify/{key}"
private const val SHOW = "show/{showId}"

private fun AnimatedContentTransitionScope<NavBackStackEntry>.toPlayer() =
    targetState.destination.route == PLAYER
private fun AnimatedContentTransitionScope<NavBackStackEntry>.fromPlayer() =
    initialState.destination.route == PLAYER

private val pageIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220)) }
private val pageOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)) }

/** Screens run edge to edge, top to bottom; the glass chrome floats over them. */
@Composable
fun AppNavHost(
    nav: NavHostController,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    remote: SpotifyRemote,
    modifier: Modifier = Modifier,
) {
    val haze = remember { HazeState() }
    val entry by nav.currentBackStackEntryAsState()
    val onPlayer = entry?.destination?.route == PLAYER
    val openPlayer = { nav.navigate(PLAYER) { launchSingleTop = true } }
    val openArtist = { id: String -> nav.navigate("artist/$id") }
    val openAlbum = { id: String -> nav.navigate("album/$id") }
    // Tapping the tab you're on goes back to its start: pages close and the list returns to the top.
    val resets = remember { mutableStateMapOf<String, Int>() }
    val reselect: (String) -> Unit = { r ->
        if (nav.currentDestination?.route == r) {
            resets[r] = (resets[r] ?: 0) + 1
        } else if (!nav.popBackStack(r, inclusive = false)) {
            nav.navigate(r) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true; restoreState = true
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = Dest.Playlists.route, modifier = Modifier.fillMaxSize().haze(haze),
            // Defaults: soft cross-fade. Screens under the player stay put while it slides.
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { if (toPlayer()) fadeOut(tween(400), targetAlpha = 1f) else fadeOut(tween(180)) },
            popEnterTransition = { if (fromPlayer()) EnterTransition.None else fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Dest.Playlists.route) {
                key(resets["playlists"]) { PlaylistsScreen(onOpenPlaylist = { id -> nav.navigate("playlist/$id") }, onOpenPlayer = openPlayer, onOpenArtist = openArtist, onOpenAlbum = openAlbum, onOpenSpotify = { item -> nav.navigate("spotify/${OpenedItems.put(item)}") }) }
            }
            composable(Dest.Search.route) {
                key(resets["search"]) { SearchScreen(onOpenPlayer = openPlayer, onOpenArtist = openArtist, onOpenAlbum = openAlbum) }
            }
            composable(Dest.Mood.route) {
                key(resets["mood"]) { MoodScreen(onOpenPlayer = openPlayer, onOpenMix = { id -> nav.navigate("mix/$id") { launchSingleTop = true } }) }
            }
            composable(Dest.Profile.route) { key(resets["profile"]) { ProfileScreen(currentSkin, onSkinChange) } }
            composable(Dest.Podcasts.route) {
                key(resets["podcasts"]) { PodcastsScreen(onOpenShow = { id -> nav.navigate("show/$id") }, onOpenPlayer = openPlayer) }
            }
            composable(SPOTIFY, enterTransition = pageIn, popExitTransition = pageOut) {
                SpotifyPageScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer)
            }
            composable(SHOW, enterTransition = pageIn, popExitTransition = pageOut) {
                ShowScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer)
            }
            composable(DETAIL, enterTransition = pageIn, popExitTransition = pageOut) {
                PlaylistDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer)
            }
            composable(MIX, enterTransition = pageIn, popExitTransition = pageOut) {
                MixDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer)
            }
            composable(ARTIST, enterTransition = pageIn, popExitTransition = pageOut) {
                ArtistScreen(onBack = { nav.popBackStack() }, onOpenAlbum = openAlbum, onOpenPlayer = openPlayer)
            }
            composable(ALBUM, enterTransition = pageIn, popExitTransition = pageOut) {
                AlbumScreen(onBack = { nav.popBackStack() }, onOpenArtist = openArtist, onOpenPlayer = openPlayer)
            }
            composable(
                PLAYER,
                enterTransition = { slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } },
            ) {
                NowPlayingScreen(onBack = { nav.popBackStack() })
            }
        }
        // Frosted band behind the clock; content scrolls under it.
        AnimatedVisibility(!onPlayer, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            TopGlass(haze)
        }
        AppChrome(nav, remote, haze, Modifier.align(Alignment.BottomCenter), onReselect = reselect)
    }
}

/**
 * Frosted glass at the bottom: the mini player and the tab bar. Content behind it is half visible
 * at the top edge and fully frosted over at the bottom. Hidden on the player.
 */
@Composable
fun AppChrome(nav: NavHostController, remote: SpotifyRemote, haze: HazeState, modifier: Modifier = Modifier, onReselect: (String) -> Unit = {}) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snap by remote.state.collectAsState()
    val art by remote.artwork.collectAsState()
    val density = LocalDensity.current
    val bg = Fidelis.bg0
    // After 10 minutes on pause the mini player tucks itself away; it comes back when music plays.
    var idle by remember { mutableStateOf(false) }
    val paused = snap?.isPaused != false
    // The whole app takes its colour and pulse from what's playing.
    LaunchedEffect(art) { Ambience.tint = art?.let { withContext(Dispatchers.Default) { artTint(it) } } }
    SideEffect { Ambience.playing = !paused }
    LaunchedEffect(paused) { idle = false; if (paused) { delay(10 * 60_000L); idle = true } }
    AnimatedVisibility(
        visible = route != PLAYER, modifier = modifier,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { ChromeInsets.bottom = with(density) { it.height.toDp() } }
                .hazeChild(haze) {
                    backgroundColor = bg
                    tints = emptyList()
                    blurRadius = 26.dp
                    noiseFactor = 0.06f
                    mask = Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color.Black)
                }
                .background(Brush.verticalGradient(
                    0f to Color.Transparent, 0.22f to bg.copy(alpha = 0.5f), 1f to bg.copy(alpha = 1f),
                ))
                .padding(top = 18.dp),
        ) {
            AnimatedVisibility(
                visible = snap?.title?.isNotBlank() == true && !idle,
                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
            ) {
                snap?.let { s ->
                    SwipeMini(
                        s, art,
                        onOpen = { nav.navigate(PLAYER) { launchSingleTop = true } },
                        onToggle = { if (s.isPaused) remote.resume() else remote.pause() },
                        onNext = { remote.skipNext() },
                        onPrev = { remote.skipPrevious() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            BottomBar(nav, onReselect)
        }
    }
}

/** Glass capsule. The selected tab grows into an accent pill with its label. */
@Composable
fun BottomBar(nav: NavHostController, onReselect: (String) -> Unit = {}) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(CircleShape)
                .background(Fidelis.bg1.copy(alpha = 0.55f))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.05f))), CircleShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dest.entries.forEach { dest ->
                val selected = route == dest.route ||
                    (dest == Dest.Playlists && (route == DETAIL || route == SPOTIFY)) || (dest == Dest.Podcasts && route == SHOW) ||
                    (dest == Dest.Mood && route == MIX) ||
                    (dest == Dest.Search && (route == ARTIST || route == ALBUM))
                Row(
                    Modifier
                        .pressable {
                            if (selected) onReselect(dest.route) else nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(Fidelis.accent) else Modifier)
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(dest.icon, contentDescription = dest.label,
                        tint = if (selected) Fidelis.ink else Fidelis.inkMute, modifier = Modifier.size(22.dp))
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(dest.label, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** Frosted band behind the clock: solid at the very top, half see-through at its lower edge. */
@Composable
private fun TopGlass(haze: HazeState) {
    val bg = Fidelis.bg0
    Box(
        Modifier.fillMaxWidth()
            .hazeChild(haze) {
                backgroundColor = bg
                tints = emptyList()
                blurRadius = 26.dp
                noiseFactor = 0.06f
                mask = Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent)
            }
            .background(Brush.verticalGradient(0f to bg, 0.78f to bg.copy(alpha = 0.5f), 1f to Color.Transparent))
            .statusBarsPadding().height(14.dp),
    )
}
