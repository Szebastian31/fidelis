package com.fidelis.player

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.fidelis.player.integration.lastfm.BridgeScrobbler
import com.fidelis.player.integration.spotify.RadioLauncher
import com.fidelis.player.integration.spotify.SpotifyKey
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.ui.navigation.AppChrome
import com.fidelis.player.ui.navigation.AppNavHost
import com.fidelis.player.ui.navigation.PLAYER
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisTheme
import com.fidelis.player.ui.theme.LocalAppMini
import com.fidelis.player.ui.theme.LocalTrackActions
import com.fidelis.player.ui.theme.Skin
import com.fidelis.player.ui.theme.TrackActions
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var spotify: SpotifyRepository
    @Inject lateinit var spotifyRemote: SpotifyRemote
    @Inject lateinit var scrobbler: BridgeScrobbler
    @Inject lateinit var radio: RadioLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.fidelis.player.audio.SoundEngine.init(this)
        // The sky runs edge to edge, behind the status and navigation bars, with light icons on top.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        scrobbler.start()

        // Android 13+: the media notification won't show without this grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android. Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
        }

        val uiPrefs = getSharedPreferences("fidelis_ui", MODE_PRIVATE)

        setContent {
            var skin by remember {
                mutableStateOf(
                    runCatching { Skin.valueOf(uiPrefs.getString("skin", null).orEmpty()) }
                        .getOrDefault(Skin.Night)
                )
            }
            FidelisTheme(skin = skin) {
                val nav = rememberNavController()
                val actions = remember(nav) {
                    TrackActions(
                        openArtist = { name -> openArtist(nav, name) },
                        songRadio = { title, artist -> radio.songRadio(title, artist) },
                        similarTo = { tracks, label -> radio.similarTo(tracks, label) },
                    )
                }
                CompositionLocalProvider(LocalAppMini provides true, LocalTrackActions provides actions) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize().background(Fidelis.skyGradient),
                    ) { padding ->
                        AppNavHost(
                            nav = nav, currentSkin = skin,
                            onSkinChange = { skin = it; uiPrefs.edit().putString("skin", it.name).apply() },
                            remote = spotifyRemote,
                        )
                    }
                }
            }
        }
    }

    /** Finds the artist on Spotify by name and opens their page (closing the player first). */
    private fun openArtist(nav: NavHostController, name: String) {
        lifecycleScope.launch {
            val id = radio.artistId(name)
            if (id == null) {
                Toast.makeText(this@MainActivity, "Couldn't find $name on Spotify", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (nav.currentDestination?.route == PLAYER) nav.popBackStack()
            nav.navigate("artist/$id") { launchSingleTop = true }
        }
    }

    /** Explicit Spotify authorization (shows the approval screen), then connect App Remote. */
    fun authorizeSpotify() {
        val clientId = SpotifyKey.clientId(this)
        if (clientId.isBlank()) { spotifyRemote.connect(this); return } // reports the missing Client ID
        spotifyRemote.markConnecting()
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            SpotifyKey.REDIRECT_URI,
        ).setScopes(arrayOf("app-remote-control", "streaming", "user-modify-playback-state")).build()
        AuthorizationClient.openLoginActivity(this, SPOTIFY_AUTH_REQUEST, request)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPOTIFY_AUTH_REQUEST) {
            val response = AuthorizationClient.getResponse(resultCode, data)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> spotifyRemote.connect(this)
                AuthorizationResponse.Type.ERROR -> spotifyRemote.reportAuthError(response.error)
                else -> spotifyRemote.reportAuthError("Authorization cancelled")
            }
        }
    }

    // Reconnect when returning; it's a no-op if already connected.
    override fun onStart() { super.onStart(); spotifyRemote.connect(this) }

    // Stay connected in the background so the notification and scrobbling keep working.
    // Only let go when the app is truly closed and nothing is playing.
    override fun onDestroy() {
        if (isFinishing && spotifyRemote.state.value?.isPaused != false) spotifyRemote.disconnect()
        super.onDestroy()
    }

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

    companion object { private const val SPOTIFY_AUTH_REQUEST = 1337 }
}
