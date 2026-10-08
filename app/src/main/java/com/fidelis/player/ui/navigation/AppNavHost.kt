package com.fidelis.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fidelis.player.ui.mood.MoodScreen
import com.fidelis.player.ui.player.NowPlayingScreen
import com.fidelis.player.ui.playlists.PlaylistDetailScreen
import com.fidelis.player.ui.playlists.PlaylistsScreen
import com.fidelis.player.ui.profile.ProfileScreen
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.Skin

private enum class Dest(val route: String, val label: String) {
    Playlists("playlists", "Playlists"), Mood("mood", "Mood"), Profile("profile", "Profile")
}

@Composable
fun AppNavHost(
    nav: NavHostController,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(nav, startDestination = Dest.Playlists.route, modifier = modifier) {
        composable(Dest.Playlists.route) {
            PlaylistsScreen(
                onOpenPlaylist = { id -> nav.navigate("playlist/$id") },
                onOpenPlayer = { nav.navigate("nowplaying") },
            )
        }
        composable(Dest.Mood.route) { MoodScreen(onOpenPlayer = { nav.navigate("nowplaying") }) }
        composable(Dest.Profile.route) { ProfileScreen(currentSkin, onSkinChange) }
        composable("playlist/{playlistId}") {
            PlaylistDetailScreen(onBack = { nav.popBackStack() }, onOpenPlayer = { nav.navigate("nowplaying") })
        }
        composable("nowplaying") { NowPlayingScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    NavigationBar(containerColor = Color.Transparent, contentColor = Fidelis.ink) {
        Dest.entries.forEach { dest ->
            val selected = current?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(dest.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                },
                icon = {
                    Icon(
                        when (dest) {
                            Dest.Playlists -> Icons.AutoMirrored.Filled.QueueMusic
                            Dest.Mood -> Icons.Filled.AutoAwesome
                            Dest.Profile -> Icons.Filled.Person
                        },
                        contentDescription = dest.label,
                    )
                },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Fidelis.ink,
                    selectedTextColor = Fidelis.ink,
                    indicatorColor = Fidelis.glass,
                    unselectedIconColor = Fidelis.inkMute,
                    unselectedTextColor = Fidelis.inkMute,
                ),
            )
        }
    }
}
