package com.fidelis.player.ui.mood

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.mood.Mix
import com.fidelis.player.mood.Mood
import com.fidelis.player.mood.Suggestion
import com.fidelis.player.ui.theme.*
import java.util.Calendar

/** Colours, icon and a one-line description for each mood. */
private data class MoodLook(val colors: List<Color>, val icon: ImageVector, val blurb: String)

private fun look(m: Mood?): MoodLook = when (m) {
    Mood.CHILL -> MoodLook(listOf(Color(0xFF2E9C8F), Color(0xFF7CC4F5)), Icons.Rounded.Spa, "Mellow, soft and unhurried")
    Mood.ENERGETIC -> MoodLook(listOf(Color(0xFFFF7A45), Color(0xFFFFC857)), Icons.Rounded.Bolt, "Upbeat songs that keep you moving")
    Mood.FOCUS -> MoodLook(listOf(Color(0xFF3A4FD8), Color(0xFF8FA8FF)), Icons.Rounded.CenterFocusStrong, "Instrumental and minimal, nothing in the way")
    Mood.PARTY -> MoodLook(listOf(Color(0xFFD9468F), Color(0xFF8B5CC7)), Icons.Rounded.Celebration, "Dance-floor picks, from club to disco")
    Mood.HAPPY -> MoodLook(listOf(Color(0xFFFFB347), Color(0xFFE4657F)), Icons.Rounded.WbSunny, "Feel-good, sunny and bright")
    Mood.MELANCHOLY -> MoodLook(listOf(Color(0xFF3B2F6B), Color(0xFF7A6BC4)), Icons.Rounded.NightsStay, "Moody, emotional, late at night")
    null -> MoodLook(listOf(Color(0xFF5B3FC4), Color(0xFFE4657F)), Icons. Rounded.AutoAwesome, "Your favourites with new finds")
}

private fun suggestionLook(s: Suggestion): MoodLook = when (s) {
    is Suggestion.MoodMix -> look(s.mood)
    Suggestion.OnRepeat -> MoodLook(listOf(Color(0xFFE4657F), Color(0xFF8B5CC7)), Icons.Rounded.Repeat, "")
    Suggestion.HalfYear -> MoodLook(listOf(Color(0xFF3A6FD8), Color(0xFF8B5CC7)), Icons.Rounded.History, "")
    Suggestion.Rediscover -> MoodLook(listOf(Color(0xFFB0476E), Color(0xFFFF9E5A)), Icons.Rounded.FavoriteBorder, "")
    else -> look(null)
}

@Composable
fun MoodScreen(onOpenPlayer: () -> Unit, onOpenMix: (String) -> Unit, vm: MoodViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by vm.connected.collectAsState()
    val progress by vm.progress.collectAsState()
    val mixes by vm.mixes.collectAsState()
    var sheetMood by remember { mutableStateOf<Mood?>(null) }
    LaunchedEffect(connected) { if (connected) vm.load() }
    LaunchedEffect(vm.openMix) { vm.openMix?.let { onOpenMix(it); vm.consumeOpen() } }

    // What fits the hour.
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (moment, heroMood) = when (hour) {
        in 5..11 -> "MORNING" to Mood.ENERGETIC
        in 12..17 -> "AFTERNOON" to Mood.CHILL
        in 18..21 -> "EVENING" to Mood.PARTY
        else -> "LATE NIGHT" to Mood.MELANCHOLY
    }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Up late"
    }
    val heroS = vm.suggestions.firstOrNull { it is Suggestion. MoodMix && it.mood == heroMood }
    val heroD = if (heroS == null || hour !in 5..21) vm.discover.firstOrNull { it.mood == heroMood } else null
    val heroTitle = heroD?.title ?: heroS?.title ?: heroMood.label
    val heroSub = heroD?.subtitle ?: heroS?.subtitle ?: look(heroMood).blurb
    val runHero: () -> Unit = { if (heroD != null) vm.runDiscover(heroD) else if (heroS != null) vm.runSuggestion(heroS) }
    val heroSaved = mixes.firstOrNull { it.title == heroTitle }

    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            item { Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader("Mood", greeting) } }

            if (!connected) {
                item {
                    EmptyState(
                        title = "Connect Spotify",
                        body = "Mixes are built from your own library and listening history.",
                        actionLabel = "Connect Spotify",
                        onAction = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl()))) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                return@LazyColumn
            }

            vm.building?.let { title ->
                item {
                    GlassCard(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment. CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Fidelis.violet, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Making “$title”", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(12.dp))
                            val p = progress
                            val bar = Modifier.fillMaxWidth().clip(CircleShape)
                            if (p != null && p.total > 0) {
                                LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = bar,
                                    color = Fidelis.violet, trackColor = Fidelis.glass)
                                Text("${p.phase} · ${p.done}/${p.total}", color = Fidelis.inkMute,
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                            } else {
                                LinearProgressIndicator(bar, color = Fidelis.violet, trackColor = Fidelis.glass)
                                Text("Gathering your songs…", color = Fidelis.inkMute,
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            vm.note?.let { item { EmptyState("No mix yet", it, Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) } }

            // ---- Right now ----
            item {
                HeroCard(
                    moment = moment, title = heroTitle, subtitle = heroSub, style = look(heroMood),
                    saved = heroSaved, busy = vm.building == heroTitle,
                    onMake = runHero,
                    onPlay = { heroSaved?.let { vm.playMix(it); onOpenPlayer() } },
                    onOpen = { heroSaved?.let { onOpenMix(it.id) } },
                )
            }

            // ---- Your mixes ----
            if (mixes.isNotEmpty()) {
                item { Label("Your mixes", "${mixes.size}") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(mixes, key = { it.id }) { m ->
                            MixCard(m, onOpen = { onOpenMix(m.id) }, onPlay = { vm.playMix(m); onOpenPlayer() })
                        }
                    }
                }
            }

            // ---- How do you feel? ----
            item { Label("How do you feel?", null) }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Mood.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { m -> MoodTile(m, Modifier.weight(1f)) { sheetMood = m } }
                        }
                    }
                }
            }

            // ---- Made for you ----
            item { Label("Made for you", null) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.suggestions, key = { it.title }) { s ->
                        val saved = mixes.firstOrNull { it.title == s.title }
                        PresetCard(s.title, s.subtitle, suggestionLook(s), null,
                            saved = saved != null, busy = vm.building == s.title,
                            onClick = { if (saved != null) onOpenMix(saved.id) else vm.runSuggestion(s) },
                            onRefresh = { vm.runSuggestion(s) })
                    }
                }
            }

            // ---- New to you ----
            item { Label("New to you", null) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.discover, key = { it.title }) { d ->
                        val saved = mixes.firstOrNull { it.title == d.title }
                        PresetCard(d.title, d.subtitle, look(d.mood), "NEW",
                            saved = saved != null, busy = vm.building == d.title,
                            onClick = { if (saved != null) onOpenMix(saved.id) else vm.runDiscover(d) },
                            onRefresh = { vm.runDiscover(d) })
                    }
                }
            }
        }
    }

    sheetMood?.let { m -> MoodSheet(m, vm, onDismiss = { sheetMood = null }) }
}

@Composable
private fun Label(text: String, count: String?) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = Fidelis.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight. ExtraBold))
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, color = Fidelis.inkFaint, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** Glossy light from the top, a little shade at the bottom. */
@Composable
private fun BoxScope.Sheen() {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.20f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
    )))
}

/** The mood's icon, large and faint, tucked into the corner. */
@Composable
private fun BoxScope.BigIcon(icon: ImageVector, size: Dp) {
    Icon(icon, null, tint = Color.White.copy(alpha = 0.16f),
        modifier = Modifier.align(Alignment.BottomEnd).offset(x = size * 0.22f, y = size * 0.22f).size(size))
}

@Composable
private fun HeroCard(
    moment: String, title: String, subtitle: String, style: MoodLook,
    saved: Mix?, busy: Boolean, onMake: () -> Unit, onPlay: () -> Unit, onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val glow = style.colors.first()
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(214.dp)
            .shadow(26.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape).background(Brush.linearGradient(style.colors))
            .pressable(enabled = !busy) { if (saved != null) onOpen() else onMake() },
    ) {
        Sheen()
        BigIcon(style.icon, 180.dp)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("RIGHT NOW · $moment", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            Spacer(Modifier.height(4.dp))
            Text(if (busy) "Making it…" else subtitle, color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(Color.White)
                        .pressable(enabled = !busy) { if (saved != null) onPlay() else onMake() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (saved != null) Icons.Rounded.PlayArrow else Icons.Rounded.AutoAwesome, null,
                        tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved != null) "Play" else "Make it", color = Color.Black, style = MaterialTheme.typography.labelLarge)
                }
                if (saved != null && !busy) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).pressable(onClick = onMake),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Refresh, "Make again", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MixCard(m: Mix, onOpen: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.width(148.dp).pressable(onClick = onOpen)) {
        Box(Modifier.size(148.dp).shadow(14.dp, shape).clip(shape)) {
            MixCollage(m.covers, Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                    .background(Color.White).pressable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play ${m.title}", tint = Color.Black, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(m.title, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (m.newCount > 0) "${m.tracks.size} songs · ${m.newCount} new" else "${m.tracks.size} songs",
            color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MoodTile(m: Mood, modifier: Modifier, onClick: () -> Unit) {
    val s = look(m)
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.height(96.dp).clip(shape).background(Brush.linearGradient(s.colors)).pressable(onClick = onClick)) {
        Sheen()
        BigIcon(s.icon, 80.dp)
        Icon(s.icon, null, tint = Color.White, modifier = Modifier.padding(14.dp).size(22.dp))
        Text(m.label, color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
    }
}

@Composable
private fun PresetCard(
    title: String, subtitle: String, style: MoodLook, badge: String?,
    saved: Boolean, busy: Boolean, onClick: () -> Unit, onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.width(164.dp).height(200.dp).clip(shape)
            .background(Brush.linearGradient(style.colors))
            .alpha(if (busy) 0.65f else 1f)
            .pressable(enabled = !busy, onClick = onClick),
    ) {
        Sheen()
        BigIcon(style.icon, 120.dp)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                badge?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text(if (busy) "Making it…" else if (saved) "Ready · tap to open" else subtitle,
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (saved && !busy) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp).size(34.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)).pressable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Refresh, "Make again", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Tap a mood: choose where the songs come from and whether to add new ones, then make the mix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodSheet(mood: Mood, vm: MoodViewModel, onDismiss: () -> Unit) {
    val s = look(mood)
    var withNew by remember { mutableStateOf(vm.includeNew) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Fidelis.bg1,
        contentColor = Fidelis.ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 10.dp).size(width = 36.dp, height = 4.dp)
                .clip(CircleShape).background(Fidelis.inkFaint))
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(124.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(s.colors)),
            ) {
                Sheen()
                BigIcon(s.icon, 130.dp)
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(mood.label, color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                    Text(s.blurb, color = Color.White.copy(alpha = 0.84f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Draw from", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.picks, key = { it.key }) { p ->
                    FidelisChip(p.label, vm.selected == p, { vm.select(p) }, Modifier.widthIn(max = 180.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add new songs", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
                    Text("Mix in songs you haven't heard yet, found through Last.fm",
                        color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = withNew, onCheckedChange = { withNew = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Fidelis.purple, checkedThumbColor = Color.White),
                )
            }
            GradientButton(
                text = if (vm.building != null) "Another mix is being made…" else "Make ${mood.label.lowercase()} mix",
                icon = Icons.Rounded.AutoAwesome,
                enabled = vm.building == null,
                onClick = {
                    if (vm.selectedMood != mood) vm.pickMood(mood)
                    if (vm.includeNew != withNew) vm.toggleNew()
                    vm.createCustom()
                    onDismiss()
                },
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            )
            Spacer(Modifier.navigationBarsPadding().height(18.dp))
        }
    }
}
