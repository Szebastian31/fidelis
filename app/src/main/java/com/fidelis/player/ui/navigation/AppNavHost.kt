package com.fidelis.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fidelis.player.ui.home.HomeScreen
import com.fidelis.player.ui.library.LibraryScreen
import com.fidelis.player.ui.player.PlayerScreen
import com.fidelis.player.ui.profile.ProfileScreen
import com.fidelis.player.ui.shuffle.SmartShuffleScreen
import com.fidelis.player.ui.theme.Skin

private enum class Dest(val route: String, val label: String) {
    Home("home", "Home"), Library("library", "Library"), Profile("profile", "Profile")
}

@Composable
fun AppNavHost(
    nav: NavHostController,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(nav, startDestination = Dest.Home.route, modifier = modifier) {
        composable(Dest.Home.route) {
            HomeScreen(
                onOpenPlayer = { nav.navigate("player") },
                onSmartShuffle = { nav.navigate("smartshuffle") },
            )
        }
        composable(Dest.Library.route) {
            LibraryScreen(onOpenPlayer = { nav.navigate("player") })
        }
        composable(Dest.Profile.route) {
            ProfileScreen(currentSkin, onSkinChange)
        }
        composable("player") {
            PlayerScreen(onBack = { nav.popBackStack() })
        }
        composable("smartshuffle") {
            SmartShuffleScreen(
                onBack = { nav.popBackStack() },
                onOpenPlayer = { nav.navigate("player") },
            )
        }
    }
}

@Composable
fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    NavigationBar {
        Dest.entries.forEach { dest ->
            val selected = current?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(dest.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        when (dest) {
                            Dest.Home -> Icons.Filled.Home
                            Dest.Library -> Icons.AutoMirrored.Filled.List
                            Dest.Profile -> Icons.Filled.Person
                        },
                        contentDescription = dest.label,
                    )
                },
                label = { Text(dest.label) },
            )
        }
    }
}
