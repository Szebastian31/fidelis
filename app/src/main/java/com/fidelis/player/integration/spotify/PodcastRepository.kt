package com.fidelis.player.integration.spotify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class PodcastShow(val id: String, val name: String, val publisher: String, val imageUrl: String?, val uri: String)

data class PodcastEpisode(
    val uri: String, val name: String, val show: String, val imageUrl: String?, val durationMs: Long,
    val released: String, val description: String, val playedMs: Long, val finished: Boolean,
)

fun PodcastEpisode.toQueue() = QueueTrack(uri, name, show, imageUrl, durationMs)

/** The shows you follow on Spotify and their episodes. */
@Singleton
class PodcastRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: SpotifyRepository,
) {
    // Kept on the phone too, so the Podcasts screens show something the moment they open.
    private val prefs = context.getSharedPreferences("podcast_cache", Context.MODE_PRIVATE)
    @Volatile private var cache: List<PodcastShow> = readShows()
    @Volatile private var latest: List<PodcastEpisode> = readEpisodes()
    fun cachedShows(): List<PodcastShow> = cache

    suspend fun shows(): List<PodcastShow> = withContext(Dispatchers.IO) {
        val items = repo.getJson("https://api.spotify.com/v1/me/shows?limit=50")?.optJSONArray("items")
            ?: return@withContext cache
        buildList { for (i in 0 until items.length()) items.optJSONObject(i)?.optJSONObject("show")?.toShow()?.let { add(it) } }
            .also { cache = it; saveShows(it) }
    }

    suspend fun show(id: String): PodcastShow? = withContext(Dispatchers.IO) {
        cache.firstOrNull { it.id == id } ?: repo.getJson("https://api.spotify.com/v1/shows/$id")?.toShow()
    }

    suspend fun episodes(show: PodcastShow, limit: Int = 50): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val items = repo.getJson("https://api.spotify.com/v1/shows/${show.id}/episodes?limit=$limit")?.optJSONArray("items")
            ?: return@withContext emptyList()
        buildList { for (i in 0 until items.length()) items.optJSONObject(i)?.toEpisode(show)?.let { add(it) } }
    }

    /** Last time's new episodes, so the list shows straight away while it refreshes. */
    fun cachedNewEpisodes(): List<PodcastEpisode> = latest

    /**
     * The newest episodes across your shows, like Spotify's "New episodes". Ones you've started come
     * first, then unplayed ones newest first; finished ones sink to the end.
     */
    suspend fun newEpisodes(shows: List<PodcastShow>): List<PodcastEpisode> = coroutineScope {
        val got = shows.take(12).map { async { runCatching { episodes(it, 3) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
        if (got.isEmpty()) return@coroutineScope latest
        got.sortedWith(compareBy<PodcastEpisode> { it.finished }
            .thenByDescending { it.playedMs > 0 }
            .thenByDescending { it.released })
            .take(20).also { latest = it; saveEpisodes(it) }
    }

    private fun saveShows(l: List<PodcastShow>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("p", it.publisher).put("i", it.imageUrl ?: "").put("u", it.uri)) }
        prefs.edit().putString("shows", a.toString()).apply()
    }

    private fun readShows(): List<PodcastShow> = runCatching {
        val a = JSONArray(prefs.getString("shows", null) ?: return@runCatching emptyList<PodcastShow>())
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o -> PodcastShow(o.getString("id"), o.optString("n"), o.optString("p"), o.optString("i").ifBlank { null }, o.optString("u")) }
        }
    }.getOrDefault(emptyList())

    private fun saveEpisodes(l: List<PodcastEpisode>) {
        val a = JSONArray()
        l.forEach { e ->
            a.put(JSONObject().put("u", e.uri).put("n", e.name).put("s", e.show).put("i", e.imageUrl ?: "")
                .put("d", e.durationMs).put("r", e.released).put("x", e.description.take(300))
                .put("p", e.playedMs).put("f", e.finished))
        }
        prefs.edit().putString("episodes", a.toString()).apply()
    }

    private fun readEpisodes(): List<PodcastEpisode> = runCatching {
        val a = JSONArray(prefs.getString("episodes", null) ?: return@runCatching emptyList<PodcastEpisode>())
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                PodcastEpisode(o.getString("u"), o.optString("n"), o.optString("s"), o.optString("i").ifBlank { null },
                    o.optLong("d"), o.optString("r"), o.optString("x"), o.optLong("p"), o.optBoolean("f"))
            }
        }
    }.getOrDefault(emptyList())
}

/** The widest image Spotify lists, rather than whichever comes first. */
private fun JSONObject.bestImage(): String? {
    val a = optJSONArray("images") ?: return null
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.maxByOrNull { it.optInt("width", 0) }
        ?.optString("url")?.ifBlank { null }
}

private fun JSONObject.toShow(): PodcastShow? {
    val id = optString("id").ifBlank { return null }
    return PodcastShow(id, optString("name"), optString("publisher"), bestImage(), optString("uri"))
}

private fun JSONObject.toEpisode(show: PodcastShow): PodcastEpisode? {
    val uri = optString("uri")
    if (!uri.startsWith("spotify:episode:")) return null
    val resume = optJSONObject("resume_point")
    return PodcastEpisode(
        uri, optString("name"), show.name, bestImage() ?: show.imageUrl, optLong("duration_ms"),
        optString("release_date"), optString("description"),
        resume?.optLong("resume_position_ms") ?: 0L, resume?.optBoolean("fully_played") ?: false,
    )
}
