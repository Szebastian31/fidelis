package com.fidelis.player.integration.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data object Loading : Lyrics
    data object None : Lyrics
    data object Instrumental : Lyrics
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val lines: List<String>) : Lyrics
}

/** Lyrics from LRCLIB (lrclib.net): free, no key. Timed lines when available, plain text otherwise. */
@Singleton
class LrcLib @Inject constructor() {
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val cache = object : LinkedHashMap<String, Lyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap. MutableEntry<String, Lyrics>?) = size > 40
    }

    suspend fun find(artist: String, title: String, durationMs: Long): Lyrics = withContext(Dispatchers.IO) {
        val key = "${artist.lowercase()}|${title.lowercase()}"
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        // A network failure isn't cached, so the next open tries again.
        val found = runCatching { search(artist, cleanTitle(title), durationMs) }.getOrElse { return@withContext Lyrics.None }
        synchronized(cache) { cache[key] = found }
        found
    }

    private fun search(artist: String, title: String, durationMs: Long): Lyrics {
        val url = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val req = Request.Builder().url(url).header("User-Agent", "Fidelis (github.com/Szebastian31/fidelis)").build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("lrclib")
            r.body?.string().orEmpty()
        }
        val arr = JSONArray(body)
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        if (items.isEmpty()) return Lyrics.None
        val secs = durationMs / 1000.0
        // Prefer timed lyrics, then the version whose length is closest to what's playing.
        val best = items.sortedWith(
            compareBy<JSONObject>({ it.str("syncedLyrics").isBlank() }, { abs(it.optDouble("duration", 0.0) - secs) })
        ).first()
        if (best.optBoolean("instrumental", false)) return Lyrics.Instrumental
        val synced = parseLrc(best.str("syncedLyrics"))
        if (synced.isNotEmpty()) return Lyrics.Synced(synced)
        val plain = best.str("plainLyrics").lines().map { it.trim() }
        return if (plain.any { it.isNotEmpty() }) Lyrics.Plain(plain) else Lyrics.None
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // org.json turns a JSON null into the text "null"; treat it as empty.
    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)

    private fun cleanTitle(t: String): String = t.replace(
        Regex("""\s*[-–(\[]\s*(\d{4}\s*)?(remaster(ed)?|radio edit|feat\.|ft\.|mono|stereo|single version).*$""",
            RegexOption.IGNORE_CASE), "",
    ).ifBlank { t }

    private companion object {
        val TAG = Regex("""\[(\d{1,3}):(\d{2}(?:[.:]\d{1,3})?)]""")

        /** "[01:02.50] line" → LyricLine(62500, "line"). A line may carry several time tags. */
        fun parseLrc(s: String): List<LyricLine> = s.lines().flatMap { line ->
            val tags = TAG.findAll(line).toList()
            if (tags.isEmpty()) return@flatMap emptyList()
            val text = line.substring(tags.last().range.last + 1).trim()
            tags.map { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].replace(':', '.').toDouble()
                LyricLine(min * 60_000 + (sec * 1000).toLong(), text)
            }
        }.sortedBy { it.timeMs }
    }
}
