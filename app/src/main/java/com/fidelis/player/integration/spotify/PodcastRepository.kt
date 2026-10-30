package com.fidelis.player.integration.spotify

import kotlinx.coroutines.Dispatchers
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
class PodcastRepository @Inject constructor(private val repo: SpotifyRepository) {
    @Volatile private var cache: List<PodcastShow> = emptyList()
    fun cachedShows(): List<PodcastShow> = cache

    suspend fun shows(): List<PodcastShow> = withContext(Dispatchers.IO) {
        val items = repo.getJson("https://api.spotify.com/v1/me/shows?limit=50")?.optJSONArray("items")
            ?: return@withContext cache
        buildList { for (i in 0 until items.length()) items.optJSONObject(i)?.optJSONObject("show")?.toShow()?.let { add(it) } }
            .also { cache = it }
    }

    suspend fun show(id: String): PodcastShow? = withContext(Dispatchers.IO) {
        cache.firstOrNull { it.id == id } ?: repo.getJson("https://api.spotify.com/v1/shows/$id")?.toShow()
    }

    suspend fun episodes(show: PodcastShow, limit: Int = 50): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val items = repo.getJson("https://api.spotify.com/v1/shows/${show.id}/episodes?limit=$limit")?.optJSONArray("items")
            ?: return@withContext emptyList()
        buildList { for (i in 0 until items.length()) items.optJSONObject(i)?.toEpisode(show)?.let { add(it) } }
    }

    /** The newest episodes across your shows, like Spotify's "New episodes". */
    suspend fun newEpisodes(shows: List<PodcastShow>): List<PodcastEpisode> = coroutineScope {
        shows.take(12).map { async { episodes(it, 3) } }.awaitAll().flatten()
            .sortedByDescending { it.released }.take(20)
    }
}

private fun JSONObject.bestImage(): String? =
    optJSONArray("images")?.takeIf { it.length() > 0 }?.optJSONObject(0)?.optString("url")?.ifBlank { null }

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
