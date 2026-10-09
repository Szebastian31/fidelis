package com.fidelis.player.integration.lastfm

import com.fidelis.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security. MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastfmApiImpl @Inject constructor() : LastfmApi {

    private val client = OkHttpClient()
    private val apiKey = BuildConfig.LASTFM_API_KEY
    private val secret = BuildConfig.LASTFM_SECRET
    private val endpoint = "https://ws.audioscrobbler.com/2.0/"

    suspend fun getMobileSession(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val params = sortedMapOf(
                "method" to "auth.getMobileSession",
                "username" to username, "password" to password, "api_key" to apiKey,
            )
            val req = Request.Builder().url(endpoint).post(formBody(signed(params))).build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("session")?.optString("key")?.ifBlank { null }
                }
            }.getOrNull()
        }

    /** Tells Last.fm what's playing right now (shows on your profile). */
    suspend fun updateNowPlaying(sk: String, artist: String, track: String, durationSec: Long): Boolean {
        val p = mutableMapOf(
            "method" to "track.updateNowPlaying", "artist" to artist, "track" to track,
            "sk" to sk, "api_key" to apiKey,
        )
        if (durationSec > 0) p["duration"] = durationSec.toString()
        return post(signed(p))
    }

    /** Logs a finished play. [timestampSec] is when the song started (UTC seconds). */
    suspend fun scrobble(sk: String, artist: String, track: String, timestampSec: Long): Boolean =
        post(signed(mutableMapOf(
            "method" to "track.scrobble", "artist" to artist, "track" to track,
            "timestamp" to timestampSec.toString(), "sk" to sk, "api_key" to apiKey,
        )))

    override suspend fun post(params: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(endpoint).post(formBody(params)).build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    !JSONObject(resp.body?.string().orEmpty()).has("error")
                }
            }.getOrDefault(false)
        }

    override suspend fun getSimilar(artist: String, track: String, limit: Int): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val url = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter("method", "track.getSimilar")
                .addQueryParameter("artist", artist).addQueryParameter("track", track)
                .addQueryParameter("autocorrect", "1").addQueryParameter("limit", limit.toString())
                .addQueryParameter("api_key", apiKey).addQueryParameter("format", "json").build()
            val req = Request.Builder().url(url).get().build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    val arr = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("similartracks")?.optJSONArray("track") ?: return@use emptyList()
                    buildList {
                        for (i in 0 until arr.length()) {
                            val t = arr.getJSONObject(i)
                            val a = t.optJSONObject("artist")?.optString("name").orEmpty()
                            val name = t.optString("name")
                            if (a.isNotBlank() && name.isNotBlank()) add(a to name)
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun getArtistTags(artist: String, limit: Int = 12): List<String> =
        topTags("artist.getTopTags", mapOf("artist" to artist), limit)

    suspend fun getTrackTags(artist: String, track: String, limit: Int = 12): List<String> =
        topTags("track.getTopTags", mapOf("artist" to artist, "track" to track), limit)

    private suspend fun topTags(method: String, args: Map<String, String>, limit: Int): List<String> =
        withContext(Dispatchers.IO) {
            if (args.values.any { it.isBlank() }) return@withContext emptyList()
            val b = endpoint.toHttpUrl().newBuilder().addQueryParameter("method", method)
            args.forEach { (k, v) -> b.addQueryParameter(k, v) }
            b.addQueryParameter("autocorrect", "1").addQueryParameter("api_key", apiKey).addQueryParameter("format", "json")
            val req = Request.Builder().url(b.build()).get().build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    val arr = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("toptags")?.optJSONArray("tag") ?: return@use emptyList()
                    buildList {
                        for (i in 0 until minOf(arr.length(), limit)) {
                            val n = arr.getJSONObject(i).optString("name")
                            if (n.isNotBlank()) add(n.lowercase())
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

    private fun signed(params: MutableMap<String, String>): Map<String, String> {
        val sig = buildString {
            params.toSortedMap().forEach { (k, v) -> append(k); append(v) }
            append(secret)
        }
        params["api_sig"] = md5(sig); params["format"] = "json"
        return params
    }

    private fun formBody(params: Map<String, String>) =
        FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
