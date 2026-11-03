package com.fidelis.player.ui.radio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fidelis.player.radio.Radio
import com.fidelis.player.radio.RadioBrowser
import com.fidelis.player.radio.RadioCountry
import com.fidelis.player.radio.RadioLibrary
import com.fidelis.player.radio.Station
import com.fidelis.player.radio.countryName
import com.fidelis.player.radio.flagOf
import com.fidelis.player.radio.homeCountry
import com.fidelis.player.radio.searchEverywhere
import com.fidelis.player.ui.podcasts. PodcastsScreen
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** The Listen tab: live radio from around the world, and your podcasts. */
@Composable
fun ListenScreen(onOpenShow: (String) -> Unit, onOpenPlayer: () -> Unit) {
    var radio by rememberSaveable { mutableStateOf(true) }
    val toggle: @Composable () -> Unit = { ListenSwitch(radio) { radio = it } }
    Crossfade(radio, animationSpec = tween(260), label = "listen") { r ->
        if (r) RadioHome(toggle) else PodcastsScreen(onOpenShow = onOpenShow, onOpenPlayer = onOpenPlayer, toggle = toggle)
    }
}

private data class Genre(val label: String, val tag: String, val a: Color, val b: Color)

private val GENRES = listOf(
    Genre("Pop", "pop", Color(0xFFFF5E8A), Color(0xFF8B5CC7)),
    Genre("Rock", "rock", Color(0xFFE4572E), Color(0xFF5A1A2E)),
    Genre("Jazz", "jazz", Color(0xFFC9A227), Color(0xFF3B2A12)),
    Genre("Classical", "classical", Color(0xFF8FA8C8), Color(0xFF2A3550)),
    Genre("Electronic", "electronic", Color(0xFF22D3EE), Color(0xFF4338CA)),
    Genre("Hip hop", "hip", Color(0xFFF59E0B), Color(0xFF7C2D12)),
    Genre("Chill", "chill", Color(0xFF34D399), Color(0xFF0F4C5C)),
    Genre("Dance", "dance", Color(0xFFEC4899), Color(0xFF6D28D9)),
    Genre("News", "news", Color(0xFF94A3B8), Color(0xFF1E293B)),
    Genre("Talk", "talk", Color(0xFF60A5FA), Color(0xFF1E3A8A)),
    Genre("Oldies", "oldies", Color(0xFFD97706), Color(0xFF451A03)),
    Genre("Metal", "metal", Color(0xFF6B7280), Color(0xFF111827)),
    Genre("J-Pop", "jpop", Color(0xFFFF7AB6), Color(0xFF3B1D6E)),
    Genre("Lo-fi", "lofi", Color(0xFFA78BFA), Color(0xFF1F2A44)),
)

private sealed interface Browse {
    val title: String
    val eyebrow: String
    data class Country(val c: RadioCountry) : Browse {
        override val title get() = "${c.flag}  ${countryName(c.code)}"
        override val eyebrow get() = "Country"
    }
    data class Tag(val g: Genre) : Browse {
        override val title get() = g.label
        override val eyebrow get() = "Genre"
    }
}

private data class Home(val featured: List<Station>, val near: List<Station>, val top: List<Station>, val countries: List<RadioCountry>)

/** Kept while the app is open, so switching tabs doesn't reload the radio. */
private object RadioCache { var home: Home? = null }

@Composable
private fun RadioHome(toggle: @Composable () -> Unit) {
    val context = LocalContext.current
    remember { RadioLibrary.load(context) }
    val here = remember { homeCountry(context) }
    var home by remember { mutableStateOf(RadioCache.home) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        if (home != null) return@LaunchedEffect
        failed = false
        runCatching {
            coroutineScope {
                val loved = async { RadioBrowser.loved(10) }
                val picks = async { runCatching { RadioBrowser.picks() }.getOrDefault(emptyList()) }
                val near = async { RadioBrowser.country(here, 40) }
                val top = async { RadioBrowser.top(30) }
                val countries = async { RadioBrowser.countries() }
                val n = near.await()
                Home(picks.await().ifEmpty { (n.take(4) + loved.await()).distinctBy { it.id }.take(9) }, n, top.await(), countries.await())
            }
        }.onSuccess { home = it; RadioCache.home = it }.onFailure { failed = true }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Station>?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(400)
        results = searchEverywhere(query.trim())
    }
    var browse by remember { mutableStateOf<Browse?>(null) }
    BackHandler(browse != null) { browse = null }
    var globe by rememberSaveable { mutableStateOf(false) }
    BackHandler(globe) { globe = false }

    FidelisBackground {
        val b = browse
        if (globe) GlobePage { globe = false }
        else if (b != null) BrowsePage(b) { browse = null }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            item { Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader("Listen", eyebrow = "Radio") { toggle() } } }
            item { SearchField(query) { query = it } }
            val res = results
            if (res != null) {
                if (res.isEmpty()) item {
                    Text("No stations by that name.", color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
                items(res, key = { "s" + it.id }) { StationRow(it) }
            } else {
                homeSections(home, failed, here, onRetry = { reload++ }, onBrowse = { browse = it }, onGlobe = { globe = true })
            }
        }
    }
}

private fun LazyListScope.homeSections(
    h: Home?, failed: Boolean, here: String, onRetry: () -> Unit, onBrowse: (Browse) -> Unit, onGlobe: () -> Unit,
) {
    when {
        h == null && failed -> item {
            EmptyState("Radio is out of reach", "Check your connection and try again.",
                Modifier.padding(20.dp), actionLabel = "Try again", onAction = onRetry)
        }
        h == null -> items(5) { i ->
            SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(if (i == 0) 176.dp else 64.dp))
        }
        else -> {
            item { Label("Fidelis picks") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(h.featured, key = { it.id }) { FeatureCard(it) }
                }
            }
            item { GlobeCard(onGlobe) }
            if (RadioLibrary.favorites.isNotEmpty()) {
                item { Label("Your stations") }
                item { TileRow(RadioLibrary.favorites) }
            }
            if (RadioLibrary.recents.isNotEmpty()) {
                item { Label("Recently played") }
                item { TileRow(RadioLibrary.recents) }
            }
            if (h.near.isNotEmpty()) {
                item {
                    LabelRow("Near you · ${flagOf(here)} ${countryName(here)}") {
                        onBrowse(Browse.Country(RadioCountry(here, countryName(here), 0)))
                    }
                }
                items(h.near.take(8), key = { "n" + it.id }) { StationRow(it) }
            }
            item { Label("Top worldwide") }
            item { TileRow(h.top) }
            item { Label("Genres") }
            items(GENRES.chunked(2)) { row ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { g -> GenreCard(g, Modifier.weight(1f)) { onBrowse(Browse.Tag(g)) } }
                }
            }
            item { Label("Countries") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(h.countries.take(50), key = { it.code }) { c -> CountryChip(c) { onBrowse(Browse.Country(c)) } }
                }
            }
        }
    }
}

@Composable
private fun BrowsePage(b: Browse, onBack: () -> Unit) {
    var list by remember(b) { mutableStateOf<List<Station>?>(null) }
    LaunchedEffect(b) {
        list = runCatching {
            when (b) {
                is Browse.Country -> RadioBrowser.country(b.c.code, 150)
                is Browse.Tag -> RadioBrowser.tag(b.g.tag, 150)
            }
        }.getOrDefault(emptyList())
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(b.eyebrow.uppercase(), color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
                    Text(b.title, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight. ExtraBold))
                }
            }
        }
        val l = list
        when {
            l == null -> items(7) { SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth().height(56.dp)) }
            l.isEmpty() -> item {
                Text("Nothing on air here right now.", color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp))
            }
            else -> items(l, key = { it.id }) { StationRow(it) }
        }
    }
}

/** A steady colour for each station, so cards without a logo still look their own. */
fun stationColor(name: String): Color {
    val h = ((name.hashCode() % 360) + 360) % 360
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 0.55f, 0.6f)))
}

/** The station's logo on white, or its initials on its own colour when it has none. */
@Composable
fun StationLogo(s: Station, size: Dp, radius: Dp) {
    var loaded by remember(s.id) { mutableStateOf(false) }
    val c = remember(s.id) { stationColor(s.name) }
    val initials = remember(s.id) {
        s.name.split(' ').filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "♪" }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius))
            .then(if (loaded) Modifier.background(Color.White)
            else Modifier.background(Brush.linearGradient(listOf(lerp(c, Color.White, 0.15f), lerp(c, Color.Black, 0.45f))))),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(initials, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.34f).sp)
        if (s.favicon.isNotBlank()) AsyncImage(
            s.favicon, null, Modifier.fillMaxSize().padding(if (loaded) size * 0.08f else 0.dp),
            contentScale = ContentScale.Fit, onSuccess = { loaded = true },
        )
    }
}

@Composable
fun LiveDot(on: Boolean) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) Color(0xFFFF4D5E).copy(alpha = a) else Fidelis.inkFaint))
}

@Composable
private fun Label(text: String) = SectionLabel(text, Modifier.padding(horizontal = 20.dp))

@Composable
private fun LabelRow(text: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text("See all", color = Fidelis.violet, style = MaterialTheme.typography.labelLarge, modifier = Modifier.pressable(onClick = onSeeAll))
    }
}

@Composable
private fun TileRow(list: List<Station>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(list, key = { it.id }) { StationTile(it) }
    }
}

@Composable
private fun FeatureCard(s: Station) {
    val context = LocalContext.current
    val c = remember(s.id) { stationColor(s.name) }
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.width(290.dp).height(176.dp).pressable { Radio.play(context, s) }
            .shadow(18.dp, shape, ambientColor = c, spotColor = c).clip(shape)
            .background(Brush.linearGradient(listOf(lerp(c, Color.White, 0.12f), lerp(c, Color.Black, 0.55f))))
            .background(Fidelis.sheen).border(1.dp, Fidelis.border, shape).padding(18.dp),
    ) {
        StationLogo(s, 64.dp, 16.dp)
        Row(Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveDot(true)
            Spacer(Modifier.width(5.dp))
            Text("LIVE", color = Fidelis.ink, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(s.name, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            Text(s.meta, color = Fidelis.inkSoft, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StationTile(s: Station) {
    val context = LocalContext.current
    Column(Modifier.width(124.dp).pressable { Radio.play(context, s) }) {
        Box(Modifier.shadow(10.dp, RoundedCornerShape(22.dp))) { StationLogo(s, 124.dp, 22.dp) }
        Spacer(Modifier.height(8.dp))
        Text(s.name, color = if (Radio.station?.id == s.id) Fidelis.violet else Fidelis.ink,
            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (s.countryCode.isNotBlank()) countryName(s.countryCode) else s.country, color = Fidelis.inkMute,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun StationRow(s: Station) {
    val context = LocalContext.current
    val current = Radio.station?.id == s.id
    val fav = RadioLibrary.isFavorite(s)
    Row(Modifier.fillMaxWidth().pressable { Radio.play(context, s) }.padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        StationLogo(s, 52.dp, 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = if (current) Fidelis.violet else Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) { LiveDot(Radio.playing); Spacer(Modifier.width(6.dp)) }
                Text(if (current) Radio.onAir ?: s.meta else s.meta, color = Fidelis.inkMute,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(40.dp).pressable { RadioLibrary.toggleFavorite(context, s) }, contentAlignment = Alignment.Center) {
            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite",
                tint = if (fav) Fidelis.pink else Fidelis.inkMute, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GenreCard(g: Genre, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.height(88.dp).pressable(onClick = onClick).clip(shape)
        .background(Brush.linearGradient(listOf(g.a, g.b))).background(Fidelis.sheen)
        .border(1.dp, Fidelis.border, shape).padding(14.dp)) {
        Text(g.label, color = Color.White, modifier = Modifier.align(Alignment.BottomStart),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun CountryChip(c: RadioCountry, onClick: () -> Unit) {
    Row(Modifier.pressable(onClick = onClick).clip(CircleShape).background(Fidelis.glass)
        .border(1.dp, Fidelis.border, CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(c.flag)
        Spacer(Modifier.width(8.dp))
        Text(countryName(c.code), color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(50.dp).clip(CircleShape).background(Fidelis.glass)
        .border(1.dp, Fidelis.border, CircleShape).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Stations from anywhere in the world", color = Fidelis.inkFaint,
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fidelis.ink), cursorBrush = SolidColor(Fidelis.violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
        }
        if (value.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = Fidelis.inkMute,
            modifier = Modifier.size(20.dp).pressable { onChange("") })
    }
}

@Composable
private fun ListenSwitch(radio: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Fidelis.glass).border(1.dp, Fidelis.border, CircleShape).padding(4.dp)) {
        listOf(true to "Radio", false to "Podcasts").forEach { (v, label) ->
            val on = v == radio
            Box(Modifier.pressable { onChange(v) }.clip(CircleShape)
                .then(if (on) Modifier.background(Fidelis.accent) else Modifier).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label, color = if (on) Fidelis.ink else Fidelis.inkMute, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The radio's mini player, above the tab bar. Tap for the full station card. */
@Composable
fun RadioMini(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val s = Radio.station ?: return
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    val tint = remember(s.id) { stationColor(s.name) }
    val busy = Radio.playing || Radio.buffering
    var found by remember { mutableStateOf<String?>(null) }
    found?.let { OnAirSheet(it, s.name, onDismiss = { found = null }) }
    Row(
        modifier.fillMaxWidth().pressable(onClick = onOpen).clip(shape)
            .background(lerp(Fidelis.bg1, tint, 0.35f).copy(alpha = 0.72f)).background(Fidelis.sheen)
            .border(1.dp, Fidelis.border, shape).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(s, 46.dp, 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(Radio.playing)
                Spacer(Modifier.width(6.dp))
                Text(Radio.onAir ?: if (Radio.buffering) "Tuning in…" else "Live · ${s.country}", color = Fidelis.inkMute,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconCircle(if (busy) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (busy) "Pause" else "Play",
            { Radio.toggle(context) }, size = 44.dp, filled = true)
        Spacer(Modifier.width(6.dp))
        Radio.onAir?.let { song -> IconCircle(Icons.Rounded.Search, "Find on Spotify", { found = song }, size = 36.dp); Spacer(Modifier.width(6.dp)) }
        IconCircle(Icons.Rounded.Close, "Stop radio", { Radio.stop() }, size = 36.dp)
        Spacer(Modifier.width(4.dp))
    }
}

/** The full station card: big logo, what's on air, favourite, play and stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSheet(onDismiss: () -> Unit) {
    val s = Radio.station
    if (s == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val context = LocalContext.current
    val c = remember(s.id) { stationColor(s.name) }
    val busy = Radio.playing || Radio.buffering
    var found by remember { mutableStateOf<String?>(null) }
    found?.let { OnAirSheet(it, s.name, onDismiss = { found = null }) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = lerp(Fidelis.bg1, c, 0.25f), contentColor = Fidelis.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.shadow(30.dp, RoundedCornerShape(36.dp), ambientColor = c, spotColor = c)) { StationLogo(s, 220.dp, 36.dp) }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(Radio.playing)
                Spacer(Modifier.width(6.dp))
                Text(if (Radio.buffering) "TUNING IN" else "LIVE", color = Fidelis.inkSoft, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(s.name, color = Fidelis.ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            Text(Radio.onAir ?: s.meta, color = Fidelis.inkMute, textAlign = TextAlign.Center, maxLines = 2,
                style = MaterialTheme.typography.bodyMedium)
            Radio.onAir?.let { song ->
                Spacer(Modifier.height(14.dp))
                SoftButton("Find on Spotify", { found = song }, icon = Icons.Rounded.Search)
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val fav = RadioLibrary.isFavorite(s)
                IconCircle(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite",
                    { RadioLibrary.toggleFavorite(context, s) }, size = 52.dp)
                IconCircle(if (busy) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause",
                    { Radio.toggle(context) }, size = 76.dp, filled = true)
                IconCircle(Icons.Rounded.Stop, "Stop", { Radio.stop(); onDismiss() }, size = 52.dp)
            }
        }
    }
}
