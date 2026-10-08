package com.fidelis.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui. Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.ui.navigation.AppNavHost
import com.fidelis.player.ui.navigation.BottomBar
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisTheme
import com.fidelis.player.ui.theme.Skin
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var spotify: SpotifyRepository
    @Inject lateinit var spotifyRemote: SpotifyRemote

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            var skin by remember { mutableStateOf(Skin.Midnight) }
            FidelisTheme(skin = skin) {
                val nav = rememberNavController()
                Scaffold(
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize().background(Fidelis.skyGradient),
                    bottomBar = { BottomBar(nav) },
                ) { padding ->
                    AppNavHost(
                        nav = nav,
                        currentSkin = skin,
                        onSkinChange = { skin = it },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    override fun onStart() { super.onStart(); spotifyRemote.connect(this) }
    override fun onStop() { spotifyRemote.disconnect(); super.onStop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "fidelis" && data.host == "auth") {
            val code = data.getQueryParameter("code") ?: return
            lifecycleScope.launch { spotify.completeAuth(code) }
        }
    }
}
