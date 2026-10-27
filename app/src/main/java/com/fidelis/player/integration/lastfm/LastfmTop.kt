package com.fidelis.player.integration.lastfm

import com.fidelis.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** An artist's most-played songs on Last.fm (all-time scrobbles), most popular first. */
@Singleton
class LastfmTop @Inject constructor() {
    private val http = OkHttpClient()

    /** (title, play count) pairs. Empty if Last.fm doesn't know the artist. */
    suspend fun topTracks(artist: String, limit: Int = 50): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext emptyList()
        val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
            .addQueryParameter("method", "artist.getTopTracks")
            .addQueryParameter("artist", artist).addQueryParameter("autocorrect", "1")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("api_key", BuildConfig.LASTFM_API_KEY)
            .addQueryParameter("format", "json").build()
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                val arr = JSONObject(r.body?.string().orEmpty()).optJSONObject("toptracks")?.optJSONArray("track")
                    ?: return@use emptyList<Pair<String, Long>>()
                (0 until arr.length()).mapNotNull { i ->
                    val t = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name = t.optString("name")
                    if (name.isBlank()) null else name to (t.optString("playcount").toLongOrNull() ?: 0L)
                }
            }
        }.getOrDefault(emptyList())
    }
}
