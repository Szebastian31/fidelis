package com.fidelis.player.integration.lyrics

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject. Singleton

private const val BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

/** A page's text, or null when it couldn't be reached. */
internal fun OkHttpClient.text(url: String, vararg headers: Pair<String, String>): String? = runCatching {
    val b = Request.Builder().url(url).header("User-Agent", BROWSER)
    headers.forEach { (k, v) -> b.header(k, v) }
    newCall(b.build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
}.getOrNull()

private val YLINE = Regex("""^\[(\d+),(\d+)](.*)$""")
private val YWORD = Regex("""\((\d+),(\d+),-?\d+\)""")

/** NetEase's word-by-word format: "[start,length](start,length,0)word(start,length,0)word…". */
internal fun parseYrc(s: String): List<LyricLine> = s.lines().mapNotNull { raw ->
    val m = YLINE.find(raw.trim()) ?: return@mapNotNull null
    val body = m.groupValues[3]
    val marks = YWORD.findAll(body).toList()
    val words = marks.mapIndexed { i, w ->
        val end = if (i + 1 < marks.size) marks[i + 1].range.first else body.length
        val st = w.groupValues[1].toLong()
        LyricWord(st, st + w.groupValues[2].toLong(), body.substring(w.range.last + 1, end))
    }
    val text = words.joinToString("") { it.text }.trim()
    if (text.isEmpty() || CREDIT.containsMatchIn(text)) null else LyricLine(m.groupValues[1].toLong(), text, words)
}.sortedBy { it.timeMs }

/** NetEase Cloud Music: a large catalogue, strong for Asian music, often with word-by-word timing. Unofficial. */
internal class NetEase(private val http: OkHttpClient) {
    private val headers = arrayOf("Referer" to "https://music.163.com/", "Cookie" to "os=pc")

    fun find(w: Wanted): List<Found>? {
        val body = http.text("https://music.163.com/api/search/get/web?s=${enc(w.mainArtist + " " + w.clean)}&type=1&limit=10&offset=0", *headers)
            ?: return null
        val songs = runCatching { JSONObject(body).optJSONObject("result")?.optJSONArray("songs") }.getOrNull() ?: return emptyList()
        val hit = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }.firstOrNull { s ->
            val ar = s.optJSONArray("artists") ?: s.optJSONArray("ar")
            val names = ar?.let { a -> (0 until a.length()).joinToString(" ") { a.optJSONObject(it)?.optString("name").orEmpty() } }.orEmpty()
            w.accepts(s.optString("name"), names, length(s))
        } ?: return emptyList()
        val id = hit.optLong("id")
        val o = http.text("https://music.163.com/api/song/lyric/v1?id=$id&cp=false&tv=0&lv=0&rv=0&kv=0&yv=0&ytv=0&yrv=0", *headers)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val diff = w.diff(length(hit))
        if (o?.optBoolean("pureMusic") == true) return listOf(Found("NetEase", 1, emptyList(), emptyList(), diff, instrumental = true))
        var lines = parseYrc(o?.optJSONObject("yrc")?.optString("lyric").orEmpty())
            .ifEmpty { parseLrc(o?.optJSONObject("lrc")?.optString("lyric").orEmpty()) }
        if (lines.isEmpty()) {
            val old = http.text("https://music.163.com/api/song/lyric?id=$id&lv=-1&kv=-1&tv=-1", *headers)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            lines = parseLrc(old?.optJSONObject("lrc")?.optString("lyric").orEmpty())
        }
        return if (lines.isEmpty()) emptyList() else listOf(Found("NetEase", 1, lines, emptyList(), diff))
    }

    private fun length(s: JSONObject) = s.optLong("duration").takeIf { it > 0 } ?: s.optLong("dt")
}

/** Kugou: another large catalogue, searched by name and length. Unofficial. */
internal class Kugou(private val http: OkHttpClient) {
    fun find(w: Wanted): List<Found>? {
        val body = http.text("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${enc(w.mainArtist + " - " + w.clean)}&duration=${w.durationMs}&hash=")
            ?: return null
        val cands = runCatching { JSONObject(body).optJSONArray("candidates") }.getOrNull() ?: return emptyList()
        val c = (0 until cands.length()).mapNotNull { cands.optJSONObject(it) }
            .firstOrNull { w.accepts(it.optString("song"), it.optString("singer"), it.optLong("duration")) } ?: return emptyList()
        val d = http.text("https://lyrics.kugou.com/download?ver=1&client=pc&id=${c.optString("id")}&accesskey=${c.optString("accesskey")}&fmt=lrc&charset=utf8")
            ?: return emptyList()
        val b64 = runCatching { JSONObject(d).optString("content") }.getOrDefault("")
        if (b64.isBlank()) return emptyList()
        val lines = parseLrc(runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(""))
        return if (lines.isEmpty()) emptyList() else listOf(Found("Kugou", 2, lines, emptyList(), w.diff(c.optLong("duration"))))
    }
}

/**
 * Finds lyrics for the song playing. Asks LRCLIB, NetEase and Kugou at once, keeps only versions
 * whose title, artist and length fit this recording, and puts the most precise timing first.
 */
@Singleton
class LyricsFinder @Inject constructor(@ApplicationContext context: Context) {
    private val http = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()
    private val lrclib = LrcLib(http)
    private val netease = NetEase(http)
    private val kugou = Kugou(http)
    private val prefs = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)
    private val cache = object : LinkedHashMap<String, List<Lyrics>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Lyrics>>?) = size > 40
    }

    fun key(artist: String, title: String) = "${artist.lowercase()}|${title.lowercase()}"

    /** Every usable version, best first. Never empty. */
    suspend fun find(artist: String, title: String, durationMs: Long): List<Lyrics> = withContext(Dispatchers.IO) {
        val k = key(artist, title)
        synchronized(cache) { cache[k] }?.let { return@withContext it }
        val w = Wanted(artist, title, durationMs)
        val results = coroutineScope {
            listOf(
                async { runCatching { lrclib.find(w) }.getOrNull() },
                async { runCatching { netease.find(w) }.getOrNull() },
                async { runCatching { kugou.find(w) }.getOrNull() },
            ).awaitAll()
        }
        // Nothing reachable, probably offline: don't remember that, so the next try asks again.
        if (results.all { it == null }) return@withContext listOf(Lyrics.None)
        val out = choose(results.filterNotNull().flatten())
        synchronized(cache) { cache[k] = out }
        out
    }

    private fun choose(found: List<Found>): List<Lyrics> {
        // Timing only fits the same recording: its length within 3 seconds, or unknown.
        val synced = found.filter { it.lines.isNotEmpty() && (it.diffMs ?: 0) <= 3_000 }
            .sortedWith(compareBy<Found>(
                { if (it.words && (it.diffMs ?: 0) <= 2_000) 0 else 1 },
                { (it.diffMs ?: 1_500) / 1_000 },
                { it.rank },
            ))
            .distinctBy { it.source }
        if (synced.isNotEmpty()) return synced.map { f ->
            Lyrics.Synced(f.lines, if (f.words) "${f.source}, word by word" else f.source, synced.size)
        }
        if (found.any { it.instrumental }) return listOf(Lyrics.Instrumental)
        // No timing fits this recording: show the words without it rather than timing that's off.
        val plain = found.sortedWith(compareBy<Found>({ it.diffMs ?: 0 }, { it.rank }))
            .map { f -> f to f.plain.ifEmpty { f.lines.map { it.text } } }
            .filter { (_, p) -> p.any { it.isNotBlank() } }
            .distinctBy { it.first.source }
        if (plain.isEmpty()) return listOf(Lyrics.None)
        return plain.map { (f, p) -> Lyrics.Plain(p, f.source, plain.size) }
    }

    private fun Lyrics.sourceName() = (this as? Lyrics.Synced)?.source ?: (this as? Lyrics.Plain)?.source

    /** The source you last picked for this song, if it still has it. */
    fun preferred(key: String, options: List<Lyrics>): Int {
        val s = prefs.getString("src:$key", null) ?: return 0
        return options.indexOfFirst { it.sourceName() == s }.coerceAtLeast(0)
    }

    fun prefer(key: String, l: Lyrics) { prefs.edit().putString("src:$key", l.sourceName()).apply() }

    /** How much earlier (positive) or later the lyrics show for this song, in ms. */
    fun offset(key: String) = prefs.getLong("off:$key", 0L)

    fun setOffset(key: String, ms: Long) {
        if (ms == 0L) prefs.edit().remove("off:$key").apply() else prefs.edit().putLong("off:$key", ms).apply()
    }
}
