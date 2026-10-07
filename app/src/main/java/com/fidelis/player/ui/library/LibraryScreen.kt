package com.fidelis.player.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.data.model.Fidelity
import com.fidelis.player.data.model.Track

@Composable
fun LibraryScreen(
    onOpenPlayer: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsState()

    val audioPerm = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[audioPerm] == true) vm.scan()
    }

    LaunchedEffect(Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(context, audioPerm) ==
            PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            vm.scan()
        } else {
            val perms = buildList {
                add(audioPerm)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "${tracks.size} tracks on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        when {
            vm.scanning && tracks.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            tracks.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No audio found. Grant the music permission to scan.")
                }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks) { index, t ->
                    TrackRow(t) { vm.play(tracks, index); onOpenPlayer() }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                t.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        FidelityBadge(t.fidelity)
    }
}

@Composable
private fun FidelityBadge(f: Fidelity) {
    val good = f.isLossless
    val bg = if (good) Color(0xFFDEF4E9) else Color(0xFFF8E8D2)
    val fg = if (good) Color(0xFF1F7A54) else Color(0xFFC9792A)
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            f.badge(),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}
