package com.fidelis.player.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fidelis.player.ui.mood.MixDetailScreen
import com.fidelis.player.ui.mood.MoodScreen
import com.fidelis.player.ui.player.NowPlayingScreen
import com.fidelis.player.ui.playlists.PlaylistDetailScreen
import com.fidelis.player.ui.playlists.PlaylistsScreen
import com.fidelis.player.ui.profile.ProfileScreen
import com.fidelis.player.ui.search.SearchScreen
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.Skin
import com.fidelis.player.ui.theme.pressable

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Playlists("playlists", "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    Search("search", "Search", Icons.Rounded.Search),
    Mood("mood", "Mood", Icons.Rounded.AutoAwesome),
    Profile("profile", "Profile", Icons.Rounded.Person),
}

private const val PLAYER = "nowplaying"
private const val DETAIL = "playlist/{playlistId}"
private const val MIX = "mix/{mixId}"

private fun AnimatedContentTransitionScope<NavBackStackEntry>.toPlayer() =
    targetState.destination.route == PLAYER
private fun AnimatedContentTransitionScope<NavBackStackEntry>.fromPlayer() =
    initialState.destination.route == PLAYER

private val pageIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220)) }
private val pageOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)) }

@Composable
fun AppNavHost(
    nav: NavHostController,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openPlayer = { nav.navigate(PLAYER) { launchSingleTop = true } }

    NavHost(
        nav, startDestination = Dest.Playlists.route, modifier = modifier,
        // Defaults: soft cross-fade. Screens under the player stay put while it slides.
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { if (toPlayer()) fadeOut(tween(400), targetAlpha = 1f) else fadeOut(tween(180)) },
        popEnterTransition = { if (fromPlayer()) EnterTransition.None else fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable(Dest.Playlists.route) {
            PlaylistsScreen(onOpenPlaylist = { id -> nav.navigate("playlist/$id") }, onOpenPlayer = openPlayer)
        }
        composable(Dest.Search.route) { SearchScreen(onOpenPlayer = openPlayer) }
        composable(Dest.Mood.route) {
            MoodScreen(onOpenPlayer = openPlayer, onOpenMix = { id -> nav.navigate("mix/$id") { launchSingleTop = true } })
        }
        composable(Dest.Profile.route) { ProfileScreen(currentSkin, onSkinChange) }

        composable(DETAIL, enterTransition = pageIn, popExitTransition = pageOut) {
            PlaylistDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer)
        }
        composable(MIX, enterTransition = pageIn, popExitTransition = pageOut) {
            MixDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = openPlayer)
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

@Composable
fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    AnimatedVisibility(
        visible = route != PLAYER,
        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
        exit = shrinkVertically(tween(300)) + fadeOut(tween(200)),
    ) {
        Column(Modifier.fillMaxWidth().background(Fidelis.bg0.copy(alpha = 0.97f))) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Fidelis.line))
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Dest.entries.forEach { dest ->
                    val selected = route == dest.route ||
                        (dest == Dest.Playlists && route == DETAIL) ||
                        (dest == Dest.Mood && route == MIX)
                    Column(
                        Modifier.pressable {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment. CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 56.dp, height = 32.dp).clip(CircleShape)
                                .then(if (selected) Modifier.background(Fidelis.glassStrong) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(dest.icon, contentDescription = dest.label,
                                tint = if (selected) Fidelis.ink else Fidelis.inkMute, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(dest.label, color = if (selected) Fidelis.ink else Fidelis.inkMute,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
