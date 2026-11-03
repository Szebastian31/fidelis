package com.fidelis.player.ui.radio

import android.content.Context
import android.graphics.Paint as NativePaint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3. MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui. Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fidelis.player.radio.RadioBrowser
import com.fidelis.player.radio.RadioGarden
import com.fidelis.player.radio.Station
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val DOT = Color(0xFF4DFFA8)
private val TAU = (2 * PI).toFloat()
private val HALF = PI.toFloat()

/** One Radio Garden place: a town or city with stations. lat and lon in degrees. */
private data class GlobePlace(val id: String, val title: String, val country: String, val lat: Float, val lon: Float, val size: Int)

/** Every place as a point on a unit sphere, plus scratch buffers for drawing. */
private class GlobeWorld(val places: List<GlobePlace>) {
    val n = places.size
    val x = FloatArray(n); val y = FloatArray(n); val z = FloatArray(n)
    val big = BooleanArray(n)
    val front = FloatArray(n * 2); val edge = FloatArray(n * 2); val bright = FloatArray(n * 2)
    init {
        places.forEachIndexed { i, p ->
            val la = Math.toRadians(p.lat.toDouble()); val lo = Math.toRadians(p.lon.toDouble())
            x[i] = (cos(la) * sin(lo)).toFloat(); y[i] = sin(la).toFloat(); z[i] = (cos(la) * cos(lo)).toFloat()
            big[i] = p.size >= 12
        }
    }
}

/**
 * Radio Garden's map of places and the stations in each. Like its search, it isn't an official
 * public API. The place list is large, so it's kept on the phone for a week.
 */
private object GardenGlobe {
    private const val API = "https://radio.garden/api"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000
    var world: GlobeWorld? = null; private set
    var lon = 0.35f
    var lat = 0.6f

    private fun fetch(url: String): String =
        RadioBrowser.http.newCall(Request.Builder().url(url).header("User-Agent", "Fidelis/1.5").build()).execute().use {
            if (!it.isSuccessful) error("Radio Garden answered ${it.code}")
            it.body?.string().orEmpty()
        }

    suspend fun load(context: Context): GlobeWorld = world ?: withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "garden_places.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK
        val body = if (fresh) file.readText() else runCatching { fetch("$API/ara/content/places").also { file.writeText(it) } }
            .getOrElse { e -> if (file.exists()) file.readText() else throw e }
        val list = JSONObject(body).getJSONObject("data").getJSONArray("list")
        val places = (0 until list.length()).mapNotNull { i ->
            val o = list.getJSONObject(i)
            val geo = o.optJSONArray("geo") ?: return@mapNotNull null
            if (geo.length() < 2) return@mapNotNull null
            GlobePlace(o.optString("id"), o.optString("title"), o.optString("country"),
                geo.optDouble(1).toFloat(), geo.optDouble(0).toFloat(), o.optInt("size", 1))
        }.filter { it.id.isNotBlank() }
        GlobeWorld(places)
    }.also { world = it }

    suspend fun stations(p: GlobePlace): List<Station> = withContext(Dispatchers.IO) {
        val data = JSONObject(fetch("$API/ara/content/page/${p.id}/channels")).optJSONObject("data") ?: return@withContext emptyList()
        val content = data.optJSONArray("content") ?: return@withContext emptyList()
        val out = mutableListOf<Station>()
        for (c in 0 until content.length()) {
            val items = content.optJSONObject(c)?.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val page = item.optJSONObject("page") ?: item
                val url = page.optString("url")
                if ("/listen/" !in url) continue
                val title = page.optString("title").ifBlank { continue }
                out += RadioGarden.station(url.substringAfterLast('/'), title, "",
                    page.optJSONObject("country")?.optString("title").orEmpty().ifBlank { p.country },
                    page.optJSONObject("place")?.optString("title").orEmpty().ifBlank { p.title })
            }
        }
        out.distinctBy { it.id }
    }
}

/** The card on the Radio home that opens the globe. */
@Composable
internal fun GlobeCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(112.dp).pressable(onClick = onClick).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF12305A), Color(0xFF060E1F)))).background(Fidelis.sheen)
            .border(1.dp, Fidelis.border, shape).padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("EXPLORE", color = DOT, style = MaterialTheme.typography.labelMedium)
            Text("Radio globe", color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            Text("Spin the Earth and tune in anywhere", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        MiniGlobe(Modifier.size(80.dp))
    }
}

@Composable
private fun MiniGlobe(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "mini")
    val phase by t.animateFloat(0f, HALF, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.92f
        val c = center
        drawCircle(Brush.radialGradient(listOf(DOT.copy(alpha = 0.25f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1F4A80), Color(0xFF081226)), c + Offset(-r * 0.4f, -r * 0.4f), r * 1.7f), r, c)
        val line = Stroke(1.dp.toPx())
        for (k in 0 until 6) {
            val w = r * abs(cos(phase + k * HALF / 6))
            drawOval(DOT.copy(alpha = 0.45f), Offset(c.x - w, c.y - r), Size(w * 2, r * 2), style = line)
        }
        for (k in listOf(-0.5f, 0f, 0.5f)) {
            val y = c.y + r * k
            val w = r * sqrt(1 - k * k)
            drawOval(DOT.copy(alpha = 0.3f), Offset(c.x - w, y - w * 0.12f), Size(w * 2, w * 0.24f), style = line)
        }
        drawCircle(Color.White.copy(alpha = 0.15f), r, c, style = line)
    }
}

/** A spinning Earth with a dot for every place that has radio, like Radio Garden. */
@Composable
internal fun GlobePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var world by remember { mutableStateOf(GardenGlobe.world) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (world != null) return@LaunchedEffect
        failed = false
        runCatching { GardenGlobe.load(context) }.onSuccess { world = it }.onFailure { failed = true }
    }

    var lon by remember { mutableFloatStateOf(GardenGlobe.lon) }
    var lat by remember { mutableFloatStateOf(GardenGlobe.lat) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var touched by remember { mutableLongStateOf(0L) }
    var picked by remember { mutableStateOf<GlobePlace?>(null) }
    val cy by animateFloatAsState(if (picked != null) 0.3f else 0.47f, tween(500), label = "cy")
    DisposableEffect(Unit) { onDispose { GardenGlobe.lon = lon; GardenGlobe.lat = lat } }
    BackHandler(picked != null) { picked = null }

    // A slow spin, which pauses while you're turning it and while a place is open.
    LaunchedEffect(picked) {
        if (picked != null) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L && System.currentTimeMillis() - touched > 2500) lon = (lon + (t - last) / 1e9f * 0.12f) % TAU
            last = t
        }
    }

    fun focus(p: GlobePlace) {
        picked = p; touched = System.currentTimeMillis()
        val tLon = Math.toRadians(p.lon.toDouble()).toFloat()
        val tLat = Math.toRadians(p.lat.toDouble()).toFloat().coerceIn(-1.3f, 1.3f)
        val sLon = lon; val sLat = lat
        var d = (tLon - sLon) % TAU
        if (d > HALF) d -= TAU
        if (d < -HALF) d += TAU
        scope.launch { animate(0f, 1f, animationSpec = tween(800)) { v, _ -> lon = sLon + d * v; lat = sLat + (tLat - sLat) * v } }
    }

    fun nearest(o: Offset, sz: IntSize, max: Float): GlobePlace? {
        val w = world ?: return null
        val r = min(sz.width, sz.height) * 0.42f * zoom
        val cx = sz.width / 2f; val cyPx = sz.height * cy
        val cl = cos(lon); val sl = sin(lon); val cp = cos(lat); val sp = sin(lat)
        var best = -1; var bestD = max * max
        for (i in 0 until w.n) {
            val x1 = w.x[i] * cl - w.z[i] * sl
            val z1 = w.z[i] * cl + w.x[i] * sl
            val z2 = w.y[i] * sp + z1 * cp
            if (z2 <= 0f) continue
            val y2 = w.y[i] * cp - z1 * sp
            val dx = cx + r * x1 - o.x; val dy = cyPx - r * y2 - o.y
            val dd = dx * dx + dy * dy
            if (dd < bestD) { bestD = dd; best = i }
        }
        return if (best >= 0) w.places[best] else null
    }

    val paint = remember { NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { strokeCap = NativePaint.Cap.ROUND } }
    val atmo = Fidelis.glow1

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, z, _ ->
                        touched = System.currentTimeMillis()
                        zoom = (zoom * z).coerceIn(1f, 8f)
                        val r = min(size.width, size.height) * 0.42f * zoom
                        lon = (lon - pan.x / r) % TAU
                        lat = (lat + pan.y / r).coerceIn(-1.4f, 1.4f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures { o -> nearest(o, size, 28.dp.toPx())?.let { focus(it) } } },
        ) {
            val r = min(size.width, size.height) * 0.42f * zoom
            val c = Offset(size.width / 2, size.height * cy)
            drawCircle(Brush.radialGradient(listOf(atmo.copy(alpha = 0.35f), Color.Transparent), c, r * 1.3f), r * 1.3f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1B3A6B), Color(0xFF071022)), c + Offset(-r * 0.4f, -r * 0.4f), r * 1.7f), r, c)
            val cl = cos(lon); val sl = sin(lon); val cp = cos(lat); val sp = sin(lat)
            world?.let { w ->
                var nf = 0; var ne = 0; var nb = 0
                for (i in 0 until w.n) {
                    val x1 = w.x[i] * cl - w.z[i] * sl
                    val z1 = w.z[i] * cl + w.x[i] * sl
                    val z2 = w.y[i] * sp + z1 * cp
                    if (z2 <= 0f) continue
                    val sx = c.x + r * x1; val sy = c.y - r * (w.y[i] * cp - z1 * sp)
                    if (sx < -20f || sx > size.width + 20f || sy < -20f || sy > size.height + 20f) continue
                    when {
                        w.big[i] && z2 > 0.25f -> { w.bright[nb++] = sx; w.bright[nb++] = sy }
                        z2 > 0.3f -> { w.front[nf++] = sx; w.front[nf++] = sy }
                        else -> { w.edge[ne++] = sx; w.edge[ne++] = sy }
                    }
                }
                val nc = drawContext.canvas.nativeCanvas
                val dot = (2.2.dp.toPx() * sqrt(zoom)).coerceAtMost(6.dp.toPx())
                paint.strokeWidth = dot
                paint.color = DOT.copy(alpha = 0.35f).toArgb(); nc.drawPoints(w.edge, 0, ne, paint)
                paint.color = DOT.copy(alpha = 0.85f).toArgb(); nc.drawPoints(w.front, 0, nf, paint)
                paint.strokeWidth = dot * 1.7f
                paint.color = Color(0xFFC4FFE0).toArgb(); nc.drawPoints(w.bright, 0, nb, paint)
            }
            drawCircle(Color.White.copy(alpha = 0.10f), r, c, style = Stroke(1.dp.toPx()))
            picked?.let { p ->
                val la = Math.toRadians(p.lat.toDouble()).toFloat(); val lo = Math.toRadians(p.lon.toDouble()).toFloat()
                val x = cos(la) * sin(lo); val y = sin(la); val z = cos(la) * cos(lo)
                val x1 = x * cl - z * sl; val z1 = z * cl + x * sl
                if (y * sp + z1 * cp > 0f) {
                    val at = Offset(c.x + r * x1, c.y - r * (y * cp - z1 * sp))
                    drawCircle(Color.White, 9.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                    drawCircle(DOT, 4.dp.toPx(), at)
                }
            }
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(world?.let { "%,d PLACES".format(it.n) } ?: "EXPLORE", color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
                Text("Radio globe", color = Fidelis.ink, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            }
            IconCircle(Icons.Rounded.Shuffle, "Random place", { world?.places?.randomOrNull()?.let { focus(it) } })
        }

        when {
            failed && world == null -> EmptyState("The globe is out of reach", "Check your connection and try again.",
                Modifier.align(Alignment.Center).padding(24.dp), actionLabel = "Try again", onAction = { attempt++ })
            world == null -> Text("Finding the world's radio…", color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center))
            picked == null -> Text("Drag to spin · pinch to zoom · tap a dot", color = Fidelis.inkMute,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = chromeBottom() + 12.dp))
        }

        picked?.let { p -> PlacePanel(p, Modifier.align(Alignment.BottomCenter)) { picked = null } }
    }
}

@Composable
private fun PlacePanel(p: GlobePlace, modifier: Modifier, onClose: () -> Unit) {
    var list by remember(p.id) { mutableStateOf<List<Station>?>(null) }
    LaunchedEffect(p.id) { list = runCatching { GardenGlobe.stations(p) }.getOrDefault(emptyList()) }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(modifier.fillMaxWidth().fillMaxHeight(0.55f).clip(shape).background(Fidelis.bg1.copy(alpha = 0.95f))
        .border(1.dp, Fidelis.border, shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.country.uppercase(), color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
                Text(p.title, color = Fidelis.ink, maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            }
            IconCircle(Icons.Rounded.Close, "Close", onClick = onClose, size = 38.dp)
        }
        val l = list
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            when {
                l == null -> items(4) { SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth().height(56.dp)) }
                l.isEmpty() -> item {
                    Text("Nothing on air here right now.", color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp))
                }
                else -> items(l, key = { it.id }) { StationRow(it) }
            }
        }
    }
}
