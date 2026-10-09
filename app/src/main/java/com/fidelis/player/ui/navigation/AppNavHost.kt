package com.fidelis.player.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import com.fidelis.player.ui.theme.pressable

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
    val route = entry?.destination?.route
    if (route == "nowplaying") return

    Column(Modifier.fillMaxWidth().background(Fidelis.bg0.copy(alpha = 0.97f))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Fidelis.line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Dest.entries.forEach { dest ->
                val selected = route == dest.route ||
                    (dest == Dest.Playlists && route?.startsWith("playlist/") == true)
                Column(
                    Modifier.pressable {
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 60.dp, height = 32.dp).clip(CircleShape)
                            .then(if (selected) Modifier.background(Fidelis.glassStrong) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when (dest) {
                                Dest.Playlists -> Icons.AutoMirrored.Rounded.QueueMusic
                                Dest.Mood -> Icons.Rounded.AutoAwesome
                                Dest.Profile -> Icons.Rounded.Person
                            },
                            contentDescription = dest.label,
                            tint = if (selected) Fidelis.ink else Fidelis.inkMute,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        dest.label,
                        color = if (selected) Fidelis.ink else Fidelis.inkMute,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
