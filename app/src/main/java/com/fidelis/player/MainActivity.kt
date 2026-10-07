package com.fidelis.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fidelis.player.ui.navigation.AppNavHost
import com.fidelis.player.ui.navigation.BottomBar
import com.fidelis.player.ui.theme.FidelisTheme
import com.fidelis.player.ui.theme.Skin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Skin selection lives in Profile; hoisted here so the whole app re-themes live.
            var skin by remember { mutableStateOf(Skin.Daylight) }
            FidelisTheme(skin = skin) {
                val nav = rememberNavController()
                Scaffold(bottomBar = { BottomBar(nav) }) { padding ->
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
}
