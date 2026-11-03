package com.fidelis.player.integration.lyrics

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.abs

/** One word of a word-by-word line, with when it starts and ends. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

sealed interface Lyrics {
    data object Loading : Lyrics
    data object None : Lyrics
    data object Instrumental : Lyrics
    /** source is where these came from; choices is how many sources had the song. */
    data class Synced(val lines: List<LyricLine>, val source: String = "", val choices: Int = 1) : Lyrics
    data class Plain(val lines: List<String>, val source: String = "", val choices: Int = 1) : Lyrics
}

/** What one source found. diffMs is how far its length is from the song playing, when known. */
internal data class Found(
    val source: String, val rank: Int, val lines: List<LyricLine>, val plain: List<String>,
    val diffMs: Long?, val instrumental: Boolean = false,
) {
    val words get() = lines.any { it.words.isNotEmpty() }
}

/** The song being looked up, cleaned for matching. */
internal class Wanted(artist: String, title: String, val durationMs: Long) {
    val clean = cleanTitle(title)
    val mainArtist = artist.split(SPLIT).first().trim().ifBlank { artist }
    private val artists = artist.split(SPLIT).map(::norm).filter { it.isNotEmpty() }
    private val t = norm(clean)

    fun titleMatches(got: String): Boolean {
        val g = norm(cleanTitle(got))
        return t.isNotEmpty() && g.isNotEmpty() && (t == g || t.contains(g) || g.contains(t))
    }

    fun artistMatches(got: String): Boolean {
        val g = norm(got)
        return g.isNotEmpty() && artists.any { g.contains(it) || it.contains(g) }
    }

    fun diff(ms: Long): Long? = if (durationMs > 0 && ms > 0) abs(ms - durationMs) else null

    /** The same song: title and artist match, or the artist matches and the length is within a second (a title in another script). */
    fun accepts(title: String, artist: String, ms: Long): Boolean {
        if (!artistMatches(artist)) return false
        val d = diff(ms)
        return (titleMatches(title) && (d == null || d <= 20_000)) || (d != null && d <= 1_000)
    }

    private companion object {
        val SPLIT = Regex("""\s*(,|&|;|/|\bfeat\.|\bft\.|\bx\b)\s*""", RegexOption.IGNORE_CASE)
    }
}

/** Lower case letters and digits only, accents removed, so names compare across sources. */
internal fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").filter { it.isLetterOrDigit() }

internal fun cleanTitle(t: String): String = t.replace(
    Regex("""\s*[-–(\[]\s*(\d{4}\s*)?(remaster(ed)?|radio edit|feat\.|ft\.|with |mono|stereo|single version|live|acoustic|explicit|from ).*$""",
        RegexOption.IGNORE_CASE), "",
).ifBlank { t }

internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

// org.json turns a JSON null into the text "null"; treat it as empty.
internal fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)

/** Song credits some sources put first, such as "作词 : …" or "Produced by …". */
internal val CREDIT = Regex(
    """^\s*(作词|作詞|作曲|编曲|編曲|制作人|制作|混音|母带|和声|录音|监制|出品|发行|词|曲|Lyrics?\s*by|Written\s*by|Composed\s*by|Produced\s*by|Arranged\s*by|Mixed\s*by)\s*[:：]""",
    RegexOption.IGNORE_CASE,
)

private val TAG = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

/** "[01:02.50] line" → LyricLine(62500, "line"). A line may carry several time tags; [offset:] is applied. */
internal fun parseLrc(s: String): List<LyricLine> {
    if (s.isBlank()) return emptyList()
    val off = OFFSET.find(s)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val out = s.lines().flatMap { line ->
        val tags = TAG.findAll(line).toList()
        if (tags.isEmpty()) return@flatMap emptyList<LyricLine>()
        val text = line.substring(tags.last().range.last + 1).replace(WORD_TAG, "").trim()
        tags.map { m ->
            val ms = m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].replace(':', '.').toDouble() * 1000).toLong()
            LyricLine((ms - off).coerceAtLeast(0), text)
        }
    }.filterNot { CREDIT.containsMatchIn(it.text) }.sortedBy { it.timeMs }
    // Every line on the same time means the file has no real timing.
    return if (out.size > 2 && out.all { it.timeMs == out.first().timeMs }) emptyList() else out
}

/** Lyrics from LRCLIB (lrclib.net): free, no key. Searched by title and artist, then by both as one query. */
internal class LrcLib(private val http: OkHttpClient) {
    fun find(w: Wanted): List<Found>? {
        val bodies = listOf(
            "https://lrclib.net/api/search?track_name=${enc(w.clean)}&artist_name=${enc(w.mainArtist)}",
            "https://lrclib.net/api/search?q=${enc(w.mainArtist + " " + w.clean)}",
        ).map { http.text(it, "User-Agent" to "Fidelis (github.com/Szebastian31/fidelis)") }
        if (bodies.all { it == null }) return null
        val items = bodies.filterNotNull().flatMap { b ->
            runCatching { JSONArray(b).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }.getOrDefault(emptyList())
        }.distinctBy { it.optLong("id") }
        return items.mapNotNull { o ->
            val ms = (o.optDouble("duration", 0.0) * 1000).toLong()
            if (!w.accepts(o.str("trackName"), o.str("artistName"), ms)) return@mapNotNull null
            Found("LRCLIB", 0, parseLrc(o.str("syncedLyrics")), o.str("plainLyrics").lines().map { it.trim() },
                w.diff(ms), o.optBoolean("instrumental", false))
        }
    }
}
