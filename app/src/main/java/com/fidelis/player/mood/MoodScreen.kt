package com.fidelis.player.ui.mood

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope. Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.mood.MOOD_ANCHORS
import com.fidelis.player.mood.Mix
import com.fidelis.player.mood.Vibe
import com.fidelis.player.mood.vibeColor
import com.fidelis.player.ui.theme.*
import java.util.Calendar

/** The Mood tab: one dial for any mood. Drag the dot, choose how much is new, make the mix. */
@Composable
fun MoodScreen(onOpenPlayer: () -> Unit, onOpenMix: (String) -> Unit, vm: MoodViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by vm.connected.collectAsState()
    val progress by vm.progress.collectAsState()
    val mixes by vm.mixes.collectAsState()
    LaunchedEffect(vm.openMix) { vm.openMix?.let { onOpenMix(it); vm.consumeOpen() } }
    val glow by animateColorAsState(vm.vibe.color, tween(500), label = "glow")
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..21 -> "Good evening"; else -> "Up late"
        }
    }

    // A small tick each time the dot crosses into another feeling.
    val haptic = LocalHapticFeedback.current
    val near = vm.vibe.nearest.label
    var lastNear by remember { mutableStateOf(near) }
    LaunchedEffect(near) { if (near != lastNear) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastNear = near } }

    FidelisBackground {
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.3f), radius = size.width * 1.1f))
        }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
                item { Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader("Mood", greeting) } }
                if (!connected) {
                    item {
                        EmptyState(
                            title = "Connect Spotify",
                            body = "Mixes are built from your own listening, with new songs if you like.",
                            actionLabel = "Connect Spotify",
                            onAction = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl()))) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    return@LazyColumn
                }
                item {
                    Text("Where are you right now?", color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp))
                }
                item { MoodDial(vm.vibe, enabled = !vm.building, onMove = vm::move, onSettle = vm::settle) }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.vibe.name, color = Fidelis.ink, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                        Text(vm.vibe.weights.take(2).joinToString(" · ") { it.first.label }, color = Fidelis.inkMute,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
                item { ShareSwitch(vm.shares, vm.share, vm::pickShare) }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
                        if (vm.building) {
                            val p = progress
                            val bar = Modifier.fillMaxWidth().clip(CircleShape)
                            if (p != null && p.total > 0) LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = bar,
                                color = glow, trackColor = Fidelis.glass)
                            else LinearProgressIndicator(bar, color = glow, trackColor = Fidelis.glass)
                            Text(p?.let { "${it.phase} · ${it.done}/${it.total}" } ?: "Gathering your songs…",
                                color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp))
                        } else {
                            GradientButton(text = "Make this mix", icon = Icons.Rounded.AutoAwesome, onClick = vm::make,
                                modifier = Modifier.fillMaxWidth())
                        }
                        vm.note?.let {
                            Text(it, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                    }
                }
                if (mixes.isNotEmpty()) {
                    item { SectionLabel("Your mixes", Modifier.padding(horizontal = 20.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(mixes, key = { it.id }) { m ->
                                MixCard(m, vm.vibeOf(m)?.color ?: Fidelis.purple,
                                    onOpen = { onOpenMix(m.id) }, onPlay = { vm.playMix(m); onOpenPlayer() })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A square colour field: calm at the bottom, hyped at the top, dark to the left, bright to the right. */
@Composable
private fun MoodDial(vibe: Vibe, enabled: Boolean, onMove: (Float, Float) -> Unit, onSettle: () -> Unit) {
    val field = remember {
        val n = 48
        val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
        for (j in 0 until n) for (i in 0 until n) b.setPixel(i, j, vibeColor(i / (n - 1f) * 2 - 1, 1 - j / (n - 1f) * 2).toArgb())
        b.asImageBitmap()
    }
    val shape = RoundedCornerShape(32.dp)
    val glow = vibe.color
    BoxWithConstraints(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(1f)
            .shadow(30.dp, shape, ambientColor = glow, spotColor = glow).clip(shape)
            .border(1.dp, Fidelis.border, shape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun at(o: Offset) = onMove(o.x / size.width * 2 - 1, 1 - o.y / size.height * 2)
                detectDragGestures(onDragStart = { at(it) }, onDragEnd = onSettle, onDragCancel = onSettle) { c, _ ->
                    c.consume(); at(c.position)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> onMove(o.x / size.width * 2 - 1, 1 - o.y / size.height * 2); onSettle() }
            },
    ) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawImage(field, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.High)
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.Black.copy(alpha = 0.22f))))
            val line = Color.White.copy(alpha = 0.12f)
            drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
            drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            val p = Offset((vibe.x + 1) / 2 * size.width, (1 - vibe.y) / 2 * size.height)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), p, 46.dp.toPx()), 46.dp.toPx(), p)
            drawCircle(Color.White, 13.dp.toPx(), p)
            drawCircle(glow, 8.dp.toPx(), p)
            drawCircle(Color.White.copy(alpha = 0.6f), 20.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
        }
        MOOD_ANCHORS.forEach { a ->
            val on = a == vibe.nearest
            Text(a.label, color = Color.White.copy(alpha = if (on) 0.95f else 0.5f), textAlign = TextAlign.Center, maxLines = 1,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                modifier = Modifier.offset(x = w * ((a.x + 1) / 2) - 45.dp, y = h * ((1 - a.y) / 2) - 9.dp).width(90.dp))
        }
        Edge("HYPED", Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        Edge("CALM", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        Edge("DARK", Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
        Edge("BRIGHT", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
    }
}

@Composable
private fun Edge(text: String, modifier: Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.labelSmall, modifier = modifier)
}

/** Just mine · A few new · Mostly new. */
@Composable
private fun ShareSwitch(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(CircleShape).background(Fidelis.glass)
        .border(1.dp, Fidelis.border, CircleShape).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(Modifier.weight(1f).clip(CircleShape).then(if (on) Modifier.background(Fidelis.accent) else Modifier)
                .pressable { onPick(i) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) Fidelis.ink else Fidelis.inkMute, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MixCard(m: Mix, tint: Color, onOpen: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.width(148.dp).pressable(onClick = onOpen)) {
        Box(Modifier.size(148.dp).shadow(14.dp, shape, ambientColor = tint, spotColor = tint).clip(shape)) {
            MixCollage(m.covers, Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.55f)))))
            Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(12.dp).clip(CircleShape).background(tint)
                .border(1.5.dp, Color.White, CircleShape))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                    .background(Color.White).pressable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play ${m.title}", tint = Color.Black, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(m.title, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (m.newCount > 0) "${m.tracks.size} songs · ${m.newCount} new" else "${m.tracks.size} songs",
            color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
    }
}
