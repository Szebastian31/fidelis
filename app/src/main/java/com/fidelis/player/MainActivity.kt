package com.fidelis.player

import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.fidelis.player.integration.lastfm.BridgeScrobbler
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.ui.navigation.AppNavHost
import com.fidelis.player.ui.navigation.BottomBar
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisTheme
import com.fidelis.player.ui.theme.Skin
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject. Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var spotify: SpotifyRepository
    @Inject lateinit var spotifyRemote: SpotifyRemote
    @Inject lateinit var scrobbler: BridgeScrobbler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        scrobbler.start()

        // Android 13+: the media notification won't show without this grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission. POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
        }

        val uiPrefs = getSharedPreferences("fidelis_ui", MODE_PRIVATE)

        setContent {
            var skin by remember {
                mutableStateOf(
                    runCatching { Skin.valueOf(uiPrefs.getString("skin", null).orEmpty()) }
                        .getOrDefault(Skin.Midnight)
                )
            }
            FidelisTheme(skin = skin) {
                val nav = rememberNavController()
                Scaffold(
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize().background(Fidelis.skyGradient),
                    bottomBar = { BottomBar(nav) },
                ) { padding ->
                    AppNavHost(
                        nav = nav, currentSkin = skin,
                        onSkinChange = { skin = it; uiPrefs.edit().putString("skin", it.name).apply() },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    /** Explicit Spotify authorization (shows the approval screen), then connect App Remote. */
    fun authorizeSpotify() {
        spotifyRemote.markConnecting()
        val request = AuthorizationRequest.Builder(
            BuildConfig.SPOTIFY_CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            "fidelis://auth",
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
