package com.fidelis.player.ui.radio

import android.content.Context
import android.graphics.Paint as NativePaint
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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val DOT = Color(0xFF4DFFA8)
private val LAND = Color(0x663A78B8)
private val COAST = Color(0x8CA9D4FF)
private val TAU = (2 * PI).toFloat()
private val HALF = PI.toFloat()

/** How far the circle in the middle reaches, in radians at normal zoom. About 500 km across. */
private const val REACH = 0.04f

/** One Radio Garden place: a town or city with stations. lat and lon in degrees. */
private data class GlobePlace(val id: String, val title: String, val country: String, val lat: Float, val lon: Float, val size: Int)

/** A place near the middle of the globe, and how far it is from it. */
private data class Near(val place: GlobePlace, val km: Int)

/** The places in the circle, or the nearest few when it's over open sea. */
private data class Region(val near: List<Near>, val inside: Boolean)

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

/** The coastlines as rings of points on a unit sphere. starts holds where each ring begins, then the end. */
private class GlobeLand(val x: FloatArray, val y: FloatArray, val z: FloatArray, val starts: IntArray)

/**
 * Radio Garden's map of places and the stations in each. Like its search, it isn't an official
 * public API. The place list is large, so it's kept on the phone for a week.
 */
private object GardenGlobe {
    private const val API = "https://radio.garden/api"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000
    private val cache = ConcurrentHashMap<String, List<Station>>()
    var world: GlobeWorld? = null; private set
    var land: GlobeLand? = null; private set
    var lon = 0.35f
    var lat = 0.6f
    var explored = false

    private fun fetch(url: String): String =
        RadioBrowser.http.newCall(Request.Builder().url(url).header("User-Agent", "Fidelis/1.6").build()).execute().use {
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

    /** The continents, from Natural Earth's public domain world map, bundled with the app. */
    suspend fun land(context: Context): GlobeLand? = land ?: withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open("land.geojson").bufferedReader().use { it.readText() }
            val fs = JSONObject(text).getJSONArray("features")
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val zs = ArrayList<Float>()
            val starts = ArrayList<Int>()
            for (f in 0 until fs.length()) {
                val g = fs.getJSONObject(f).optJSONObject("geometry") ?: continue
                val c = g.optJSONArray("coordinates") ?: continue
                val polys = if (g.optString("type") == "Polygon") listOf(c) else (0 until c.length()).map { c.getJSONArray(it) }
                for (p in polys) for (r in 0 until p.length()) {
                    val ring = p.getJSONArray(r)
                    if (ring.length() < 3) continue
                    starts += xs.size
                    for (k in 0 until ring.length()) {
                        val pt = ring.getJSONArray(k)
                        val la = Math.toRadians(pt.getDouble(1)); val lo = Math.toRadians(pt.getDouble(0))
                        xs += (cos(la) * sin(lo)).toFloat(); ys += sin(la).toFloat(); zs += (cos(la) * cos(lo)).toFloat()
                    }
                }
            }
            starts += xs.size
            GlobeLand(xs.toFloatArray(), ys.toFloatArray(), zs.toFloatArray(), starts.toIntArray())
        }.getOrNull()
    }.also { land = it }

    suspend fun stations(p: GlobePlace): List<Station> = cache[p.id] ?: withContext(Dispatchers.IO) {
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
                val title = page.optString("title")
                if (title.isBlank()) continue
                out += RadioGarden.station(url.substringAfterLast('/'), title, "",
                    page.optJSONObject("country")?.optString("title").orEmpty().ifBlank { p.country },
                    page.optJSONObject("place")?.optString("title").orEmpty().ifBlank { p.title })
            }
        }
        out.distinctBy { it.id }.also { cache[p.id] = it }
    }
}

/** The places around the middle of the globe, nearest first. */
private fun nearby(w: GlobeWorld, la: Float, lo: Float, zoom: Float): Region {
    val cx = cos(la) * sin(lo); val cy = sin(la); val cz = cos(la) * cos(lo)
    val d = FloatArray(w.n) { w.x[it] * cx + w.y[it] * cy + w.z[it] * cz }
    val order = (0 until w.n).sortedByDescending { d[it] }
    val limit = cos(REACH * 3 / zoom)
    val inside = order.takeWhile { d[it] >= limit }.take(8)
    val pick = inside.ifEmpty { order.take(3) }
    return Region(pick.map { Near(w.places[it], (acos(d[it].coerceIn(-1f, 1f)) * 6371).roundToInt()) }, inside.isNotEmpty())
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

/**
 * A spinning Earth with its continents and a dot for every place that has radio, like Radio Garden.
 * Whatever sits in the circle in the middle is listed below, so there's no need to hit a tiny dot.
 */
@Composable
internal fun GlobePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var world by remember { mutableStateOf(GardenGlobe.world) }
    var land by remember { mutableStateOf(GardenGlobe.land) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (land == null) land = GardenGlobe.land(context) }
    LaunchedEffect(attempt) {
        if (world != null) return@LaunchedEffect
        failed = false
        runCatching { GardenGlobe.load(context) }.onSuccess { world = it }.onFailure { failed = true }
    }

    var lon by remember { mutableFloatStateOf(GardenGlobe.lon) }
    var lat by remember { mutableFloatStateOf(GardenGlobe.lat) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var exploring by remember { mutableStateOf(GardenGlobe.explored) }
    var region by remember { mutableStateOf<Region?>(null) }
    val cy by animateFloatAsState(if (exploring) 0.3f else 0.47f, tween(500), label = "cy")
    DisposableEffect(Unit) { onDispose { GardenGlobe.lon = lon; GardenGlobe.lat = lat; GardenGlobe.explored = exploring } }

    // A slow spin until you first touch it, so the list below doesn't keep changing.
    LaunchedEffect(exploring) {
        if (exploring) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) lon = (lon + (t - last) / 1e9f * 0.12f) % TAU
            last = t
        }
    }

    // Once the globe settles, list what's in the circle.
    LaunchedEffect(world, exploring) {
        val w = world ?: return@LaunchedEffect
        if (!exploring) return@LaunchedEffect
        snapshotFlow { Triple(lat, lon, zoom) }.collectLatest { (la, lo, z) ->
            delay(400)
            region = withContext(Dispatchers.Default) { nearby(w, la, lo, z) }
        }
    }

    /** Swings the globe so this spot sits in the circle. */
    fun go(tLat: Float, tLon: Float) {
        exploring = true
        val sLon = lon; val sLat = lat
        val la = tLat.coerceIn(-1.4f, 1.4f)
        var d = (tLon - sLon) % TAU
        if (d > HALF) d -= TAU
        if (d < -HALF) d += TAU
        scope.launch { animate(0f, 1f, animationSpec = tween(700)) { v, _ -> lon = sLon + d * v; lat = sLat + (la - sLat) * v } }
    }

    /** The spot on the Earth under a tap, as lat and lon in radians. */
    fun spot(o: Offset, sz: IntSize): Pair<Float, Float>? {
        val r = min(sz.width, sz.height) * 0.42f * zoom
        val x1 = (o.x - sz.width / 2f) / r; val y2 = (sz.height * cy - o.y) / r
        val q = x1 * x1 + y2 * y2
        if (q > 1f) return null
        val z2 = sqrt(1 - q)
        val cl = cos(lon); val sl = sin(lon); val cp = cos(lat); val sp = sin(lat)
        val y = y2 * cp + z2 * sp
        val z1 = -y2 * sp + z2 * cp
        val x = x1 * cl + z1 * sl
        val z = z1 * cl - x1 * sl
        return asin(y.coerceIn(-1f, 1f)) to atan2(x, z)
    }

    val paint = remember { NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { strokeCap = NativePaint.Cap.ROUND } }
    val fill = remember { Path() }
    val coast = remember { Path() }
    val atmo = Fidelis.glow1

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, z, _ ->
                        exploring = true
                        zoom = (zoom * z).coerceIn(1f, 8f)
                        val r = min(size.width, size.height) * 0.42f * zoom
                        lon = (lon - pan.x / r) % TAU
                        lat = (lat + pan.y / r).coerceIn(-1.4f, 1.4f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures { o -> spot(o, size)?.let { (la, lo) -> go(la, lo) } } },
        ) {
            val base = min(size.width, size.height) * 0.42f
            val r = base * zoom
            val c = Offset(size.width / 2, size.height * cy)
            drawCircle(Brush.radialGradient(listOf(atmo.copy(alpha = 0.35f), Color.Transparent), c, r * 1.3f), r * 1.3f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1B3A6B), Color(0xFF071022)), c + Offset(-r * 0.4f, -r * 0.4f), r * 1.7f), r, c)
            val cl = cos(lon); val sl = sin(lon); val cp = cos(lat); val sp = sin(lat)

            // The continents. Points round the back are pinned to the edge so the shapes stay closed.
            val g = land
            if (g != null) {
                fill.reset(); coast.reset()
                for (k in 0 until g.starts.size - 1) {
                    val a = g.starts[k]; val b = g.starts[k + 1]
                    var seen = false
                    for (i in a until b) if (g.y[i] * sp + (g.z[i] * cl + g.x[i] * sl) * cp > 0f) { seen = true; break }
                    if (!seen) continue
                    var pen = false
                    for (i in a until b) {
                        val x1 = g.x[i] * cl - g.z[i] * sl
                        val z1 = g.z[i] * cl + g.x[i] * sl
                        var px = x1; var py = g.y[i] * cp - z1 * sp
                        val vis = g.y[i] * sp + z1 * cp > 0f
                        if (!vis) { val m = sqrt(px * px + py * py).coerceAtLeast(1e-4f); px /= m; py /= m }
                        val sx = c.x + r * px; val sy = c.y - r * py
                        if (i == a) fill.moveTo(sx, sy) else fill.lineTo(sx, sy)
                        if (vis && pen) coast.lineTo(sx, sy) else if (vis) coast.moveTo(sx, sy)
                        pen = vis
                    }
                    fill.close()
                }
                drawPath(fill, LAND)
                drawPath(coast, COAST, style = Stroke(1.dp.toPx()))
            }

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

            if (exploring) {
                // The places in the list light up, and the circle shows what's being listed.
                region?.near?.forEach { n ->
                    val la = Math.toRadians(n.place.lat.toDouble()).toFloat(); val lo = Math.toRadians(n.place.lon.toDouble()).toFloat()
                    val x = cos(la) * sin(lo); val y = sin(la); val z = cos(la) * cos(lo)
                    val x1 = x * cl - z * sl; val z1 = z * cl + x * sl
                    if (y * sp + z1 * cp > 0f) drawCircle(Color.White, 3.5.dp.toPx(), Offset(c.x + r * x1, c.y - r * (y * cp - z1 * sp)))
                }
                drawCircle(Color.White.copy(alpha = 0.85f), base * REACH * 3, c, style = Stroke(1.5.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.08f), base * REACH * 3, c)
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
            IconCircle(Icons.Rounded.Shuffle, "Random place", {
                world?.places?.randomOrNull()?.let { p ->
                    go(Math.toRadians(p.lat.toDouble()).toFloat(), Math.toRadians(p.lon.toDouble()).toFloat())
                }
            })
        }

        val reg = region
        when {
            failed && world == null -> EmptyState("The globe is out of reach", "Check your connection and try again.",
                Modifier.align(Alignment.Center).padding(24.dp), actionLabel = "Try again", onAction = { attempt++ })
            world == null -> Text("Finding the world's radio…", color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center))
            !exploring -> Text("Drag the globe or tap anywhere to tune in", color = Fidelis.inkMute,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = chromeBottom() + 12.dp))
            reg != null -> RegionPanel(reg, Modifier.align(Alignment. BottomCenter))
        }
    }
}

/** The stations in and around the circle, grouped by place, nearest first. */
@Composable
private fun RegionPanel(region: Region, modifier: Modifier) {
    var lists by remember(region) { mutableStateOf<Map<String, List<Station>>>(emptyMap()) }
    LaunchedEffect(region) {
        region.near.forEach { n ->
            launch {
                val l = runCatching { GardenGlobe.stations(n.place) }.getOrDefault(emptyList())
                lists = lists + (n.place.id to l)
            }
        }
    }
    val first = region.near.first().place
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(modifier.fillMaxWidth().fillMaxHeight(0.5f).clip(shape).background(Fidelis.bg1.copy(alpha = 0.95f))
        .border(1.dp, Fidelis.border, shape)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)) {
            Text(if (region.inside) "NEAR · ${first.country.uppercase()}" else "OPEN SEA · NEAREST RADIO",
                color = Fidelis.violet, style = MaterialTheme.typography.labelMedium)
            Text(first.title, color = Fidelis.ink, maxLines = 1,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            region.near.forEach { n ->
                val id = n.place.id
                item(key = "h$id") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(n.place.title, color = Fidelis.ink, maxLines = 1, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(if (n.km < 1) "here" else "%,d km".format(n.km), color = Fidelis.inkMute,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
                val l = lists[id]
                when {
                    l == null -> item(key = "s$id") {
                        SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth().height(56.dp))
                    }
                    l.isEmpty() -> item(key = "e$id") {
                        Text("Nothing on air here right now.", color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                    else -> items(l, key = { id + it.id }) { StationRow(it) }
                }
            }
        }
    }
}
