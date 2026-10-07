package com.fidelis.player.ui.player

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.data.model.Fidelity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val s = ui.state

    if (!s.hasMedia) {
        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text("Nothing playing yet. Pick a track from your Library.")
        }
        return
    }

    val art = rememberAlbumArt(ui.current?.uri)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(s.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(
            s.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )

        ui.current?.fidelity?.let {
            Spacer(Modifier.height(10.dp))
            FidelityBadge(it)
        }

        Spacer(Modifier.height(20.dp))
        val duration = s.durationMs.coerceAtLeast(1L)
        var dragging by remember { mutableStateOf<Float?>(null) }
        val sliderValue = dragging ?: (vm.position.toFloat() / duration).coerceIn(0f, 1f)
        Slider(
            value = sliderValue,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { vm.seekTo((it * duration).toLong()) }
                dragging = null
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(vm.position), style = MaterialTheme.typography.labelSmall)
            Text(formatMs(s.durationMs), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::previous) {
                Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(16.dp))
            FilledIconButton(onClick = vm::playPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or pause",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = vm::next) {
                Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun rememberAlbumArt(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val art by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(uri))
                    retriever.embeddedPicture?.let {
                        BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                    }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    return art
}

@Composable
private fun FidelityBadge(f: Fidelity) {
    val good = f.isLossless
    val bg = if (good) Color(0xFFDEF4E9) else Color(0xFFF8E8D2)
    val fg = if (good) Color(0xFF1F7A54) else Color(0xFFC9792A)
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            f.badge(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
