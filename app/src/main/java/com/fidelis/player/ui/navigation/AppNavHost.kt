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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
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
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.MiniPlayerPill
import com.fidelis.player.ui.theme.Skin
import com.fidelis.player.ui.theme.pressable

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Playlists("playlists", "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    Search("search", "Search", Icons.Rounded.Search),
    Mood("mood", "Mood", Icons.Rounded.AutoAwesome),
    Profile("profile", "Profile", Icons.Rounded.Person),
}

const val PLAYER = "nowplaying"
private const val DETAIL = "playlist/{playlistId}"
private const val MIX = "mix/{mixId}"
private const val ARTIST = "artist/{artistId}"
private const val ALBUM = "album/{albumId}"

private fun AnimatedContentTransitionScope<NavBackStackEntry>.toPlayer() =
    targetState.destination.route == PLAYER
private fun AnimatedContentTransitionScope<NavBackStackEntry>.fromPlayer() =
    initialState.destination.route == PLAYER

private val pageIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220)) }
private val pageOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)) }

/** Screens sit inside the system bars and above the mini player; the player runs edge to edge. */
@Composable
private fun Pad(p: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(p)) { content() }
}

@Composable
fun AppNavHost(
    nav: NavHostController,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val openPlayer = { nav.navigate(PLAYER) { launchSingleTop = true } }
    val openArtist = { id: String -> nav.navigate("artist/$id") }
    val openAlbum = { id: String -> nav.navigate("album/$id") }
    val p = contentPadding
    NavHost(
        nav, startDestination = Dest.Playlists.route, modifier = modifier,
        // Defaults: soft cross-fade. Screens under the player stay put while it slides.
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { if (toPlayer()) fadeOut(tween(400), targetAlpha = 1f) else fadeOut(tween(180)) },
        popEnterTransition = { if (fromPlayer()) EnterTransition.None else fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable(Dest.Playlists.route) {
            Pad(p) { PlaylistsScreen(onOpenPlaylist = { id -> nav.navigate("playlist/$id") }, onOpenPlayer = openPlayer, onOpenArtist = openArtist) }
        }
        composable(Dest.Search.route) {
            Pad(p) { SearchScreen(onOpenPlayer = openPlayer, onOpenArtist = openArtist, onOpenAlbum = openAlbum) }
        }
        composable(Dest.Mood.route) {
            Pad(p) { MoodScreen(onOpenPlayer = openPlayer, onOpenMix = { id -> nav.navigate("mix/$id") { launchSingleTop = true } }) }
        }
        composable(Dest.Profile.route) { Pad(p) { ProfileScreen(currentSkin, onSkinChange) } }
        composable(DETAIL, enterTransition = pageIn, popExitTransition = pageOut) {
            Pad(p) { PlaylistDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer) }
        }
        composable(MIX, enterTransition = pageIn, popExitTransition = pageOut) {
            Pad(p) { MixDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer) }
        }
        composable(ARTIST, enterTransition = pageIn, popExitTransition = pageOut) {
            Pad(p) { ArtistScreen(onBack = { nav.popBackStack() }, onOpenAlbum = openAlbum, onOpenPlayer = openPlayer) }
        }
        composable(ALBUM, enterTransition = pageIn, popExitTransition = pageOut) {
            Pad(p) { AlbumScreen(onBack = { nav.popBackStack() }, onOpenArtist = openArtist, onOpenPlayer = openPlayer) }
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
}

/** Everything under the screens: the shared mini player, then the tab bar. Both hide on the player. */
@Composable
fun AppChrome(nav: NavHostController, remote: SpotifyRemote) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snap by remote.state.collectAsState()
    val art by remote.artwork.collectAsState()
    Column {
        AnimatedVisibility(
            visible = route != PLAYER && snap?.title?.isNotBlank() == true,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(260)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            snap?.let { s ->
                MiniPlayerPill(
                    s, art,
                    onOpen = { nav.navigate(PLAYER) { launchSingleTop = true } },
                    onToggle = { if (s.isPaused) remote.resume() else remote.pause() },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
                )
            }
        }
        BottomBar(nav)
    }
}

/** Floating glass capsule. The selected tab grows into an accent pill with its label. */
@Composable
fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    AnimatedVisibility(
        visible = route != PLAYER,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200)),
    ) {
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.shadow(24.dp, CircleShape).clip(CircleShape)
                    .background(Fidelis.bg1.copy(alpha = 0.94f)).border(1.dp, Fidelis.border, CircleShape)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dest.entries.forEach { dest ->
                    val selected = route == dest.route ||
                        (dest == Dest.Playlists && route == DETAIL) ||
                        (dest == Dest.Mood && route == MIX) ||
                        (dest == Dest.Search && (route == ARTIST || route == ALBUM))
                    Row(
                        Modifier
                            .pressable {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            }
                            .clip(CircleShape)
                            .then(if (selected) Modifier.background(Fidelis.accent) else Modifier)
                            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
}
