package com.fidelis.player.integration.spotify

import android.content.Context
import com.fidelis.player.radio.RadioBrowser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A row of suggested shows, like "Because you listen to" and the show's name. */
data class PodcastRow(val key: String, val eyebrow: String, val title: String, val shows: List<PodcastShow>)
data class PodcastPicks(val vibe: List<String>, val rows: List<PodcastRow>)

/**
 * Finding new podcasts. Spotify has no podcast recommendations in its API, so each of your shows
 * gets its genre from Apple Podcasts' public search, kept on the phone, and Spotify is searched with that.
 */
@Singleton
class PodcastDiscovery @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: SpotifyRepository,
) {
    private val prefs = context.getSharedPreferences("podcast_genres", Context.MODE_PRIVATE)

    /** Any show on Spotify, by name, host or topic. */
    suspend fun search(q: String): List<PodcastShow> = coroutineScope {
        if (q.isBlank()) return@coroutineScope emptyList()
        val enc = URLEncoder.encode(q.trim(), "UTF-8")
        listOf(0, 10).map { off ->
            async(Dispatchers.IO) {
                runCatching {
                    val items = repo.getJson("https://api.spotify.com/v1/search?q=$enc&type=show&market=from_token&limit=10&offset=$off")
                        ?.optJSONObject("shows")?.optJSONArray("items")
                    buildList { if (items != null) for (i in 0 until items.length()) items.optJSONObject(i)?.asShow()?.let { add(it) } }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    /** The show's genre, such as "Comedy" or "True Crime", or null when Apple doesn't know it. */
    suspend fun genre(s: PodcastShow): String? = withContext(Dispatchers.IO) {
        prefs.getString(s.id, null)?.let { return@withContext it.ifBlank { null } }
        val g = try { lookup(s) } catch (e: Exception) { return@withContext null }
        prefs.edit().putString(s.id, g.orEmpty()).apply()
        g
    }

    private fun lookup(s: PodcastShow): String? {
        val cc = Locale.getDefault().country.ifBlank { "US" }
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=5&country=$cc&term=" +
            URLEncoder.encode(s.name, "UTF-8")
        val body = RadioBrowser.http.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) error("Apple answered ${it.code}")
            it.body?.string().orEmpty()
        }
        val res = JSONObject(body).optJSONArray("results") ?: return null
        val want = podKey(s.name)
        if (want.isEmpty()) return null
        for (i in 0 until res.length()) {
            val o = res.optJSONObject(i) ?: continue
            val got = podKey(o.optString("collectionName"))
            if (got.isNotEmpty() && (got == want || got.startsWith(want) || want.startsWith(got)))
                return o.optString("primaryGenreName").takeIf { it.isNotBlank() && it != "Podcasts" }
        }
        return null
    }

    /** Your top genres and rows of shows like yours. Shows you already follow never appear. */
    suspend fun forYou(shows: List<PodcastShow>, favourite: PodcastShow?): PodcastPicks = coroutineScope {
        if (shows.isEmpty()) return@coroutineScope PodcastPicks(emptyList(), emptyList())
        val tagged = shows.take(15).map { s -> async { s to genre(s) } }.awaitAll()
        val vibe = tagged.mapNotNull { it.second }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }.take(4)
        val fav = favourite ?: shows.first()
        val favGenre = tagged.firstOrNull { it.first.id == fav.id }?.second
        val similar = async { podMix(search(fav.name), favGenre?.let { search(it) }.orEmpty()) }
        val people = async {
            shows.filter { it.publisher.isNotBlank() }.take(4)
                .map { s -> async { s to search(s.publisher).filter { podKey(it.publisher) == podKey(s.publisher) } } }
                .awaitAll()
        }
        val moods = vibe.take(2).map { g -> async { g to search(g) } }

        val seen = shows.map { it.id }.toHashSet()
        fun fresh(l: List<PodcastShow>) = l.filter { seen.add(it.id) }.take(12)
        val rows = mutableListOf<PodcastRow>()
        fresh(similar.await()).takeIf { it.isNotEmpty() }?.let { rows += PodcastRow("similar", "Because you listen to", fav.name, it) }
        for ((s, l) in people.await()) {
            val f = fresh(l)
            if (f.isNotEmpty()) { rows += PodcastRow("people", "From the people behind", s.name, f); break }
        }
        moods.awaitAll().forEach { (g, l) ->
            fresh(l).takeIf { it.isNotEmpty() }?.let { rows += PodcastRow("vibe$g", "More in your vibe", g, it) }
        }
        PodcastPicks(vibe, rows)
    }
}

private fun podKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

private fun podMix(a: List<PodcastShow>, b: List<PodcastShow>) = buildList {
    for (i in 0 until maxOf(a.size, b.size)) { a.getOrNull(i)?.let(::add); b.getOrNull(i)?.let(::add) }
}

private fun JSONObject.asShow(): PodcastShow? {
    val id = optString("id")
    if (id.isBlank()) return null
    val img = optJSONArray("images")?.optJSONObject(0)?.optString("url")?.ifBlank { null }
    return PodcastShow(id, optString("name"), optString("publisher"), img, optString("uri"))
}
