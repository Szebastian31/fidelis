package com.fidelis.player.integration.spotify

import android.content.Context
import com.fidelis.player.BuildConfig
import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.data.db.SpotifyMatchEntity
import com.fidelis.player.data.secure.SecureTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3. RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val total: Int, val matched: Int, val gaps: Int)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val uri: String,
)

data class PlaylistTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val durationMs: Long = 0L,
)

fun PlaylistTrack.toQueue() = QueueTrack(uri, title, artist, imageUrl, durationMs)

@Singleton
class SpotifyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: SecureTokenStore,
    private val matcher: SpotifyMatcher,
    private val dao: MusicDao,
) {
    private val client = OkHttpClient()
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "fidelis://auth"
    private val refreshLock = Any()

    private val _connected = MutableStateFlow(tokenStore.spotifyAccessToken != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    // Last good playlist list, so a flaky network never blanks the grid.
    @Volatile private var cache: List<SpotifyPlaylist> = emptyList()
    fun cachedPlaylists(): List<SpotifyPlaylist> = cache
    fun cachedPlaylist(id: String): SpotifyPlaylist? = cache.firstOrNull { it.id == id }

    fun authUrl(): String {
        val pkce = SpotifyPkce(clientId, redirectUri)
        tokenStore.spotifyCodeVerifier = pkce.codeVerifier
        return pkce.buildAuthUrl(state = System.currentTimeMillis().toString())
    }

    suspend fun completeAuth(code: String): Boolean = withContext(Dispatchers.IO) {
        val verifier = tokenStore.spotifyCodeVerifier ?: return@withContext false
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code").add("code", code)
            .add("redirect_uri", redirectUri).add("client_id", clientId)
            .add("code_verifier", verifier).build()
        val req = Request.Builder().url(TOKEN_URL).post(body).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty())
                val access = json.optString("access_token")
                if (access.isBlank()) return@use false
                tokenStore.spotifyAccessToken = access
                tokenStore.spotifyRefreshToken = json.optString("refresh_token").ifBlank { null }
                _connected.value = true
                true
            }
        }.getOrDefault(false)
    }

    fun disconnect() {
        tokenStore.spotifyAccessToken = null
        tokenStore.spotifyRefreshToken = null
        cache = emptyList()
        _connected.value = false
    }

    // ---------- HTTP with automatic token refresh ----------

    private fun authed(url: String, token: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $token").build()

    /** GET that transparently refreshes the 1-hour access token on 401. Null on failure. */
    internal fun getJson(url: String): JSONObject? = runCatching {
        val token = tokenStore.spotifyAccessToken ?: return null
        val first = client.newCall(authed(url, token)).execute()
        val resp = if (first.code == 401) {
            first.close()
            val fresh = refreshAccessToken(token) ?: return null
            client.newCall(authed(url, fresh)).execute()
        } else first
        resp.use { if (it.isSuccessful) JSONObject(it.body?.string().orEmpty()) else null }
    }.getOrNull()

    /** Any Web API call; refreshes the token on 401. Returns the HTTP code and body (0 = no network). */
    internal fun send(method: String, url: String, json: String? = null): Pair<Int, String> = runCatching {
        val token = tokenStore.spotifyAccessToken ?: return 401 to ""
        fun req(t: String) = Request.Builder().url(url).header("Authorization", "Bearer $t")
            .method(method, json?.toRequestBody("application/json".toMediaType()) ?: if (method == "GET") null else ByteArray(0).toRequestBody())
            .build()
        val first = client.newCall(req(token)).execute()
        val resp = if (first.code == 401) {
            first.close()
            val fresh = refreshAccessToken(token) ?: return 401 to ""
            client.newCall(req(fresh)).execute()
        } else first
        resp.use { it.code to it.body?.string().orEmpty() }
    }.getOrDefault(0 to "")

    private fun refreshAccessToken(expired: String): String? = synchronized(refreshLock) {
        // Another request may have refreshed while we waited.
        tokenStore.spotifyAccessToken?.let { if (it != expired) return it }
        val refresh = tokenStore.spotifyRefreshToken ?: run { signOut(); return null }
        val body = FormBody. Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId).build()
        val json = runCatching {
            client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
                .use { JSONObject(it.body?.string().orEmpty()) }
        }.getOrNull() ?: return null // network blip: stay signed in, try again later
        val access = json.optString("access_token")
        if (access.isBlank()) { signOut(); return null } // refresh revoked → show Connect again
        tokenStore.spotifyAccessToken = access
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { tokenStore.spotifyRefreshToken = it }
        access
    }

    private fun signOut() {
        tokenStore.spotifyAccessToken = null
        tokenStore.spotifyRefreshToken = null
        _connected.value = false
    }

    /** Walks `next` links. Returns false if the very first page failed. */
    private fun paged(start: String, maxPages: Int, onPage: (JSONObject) -> Unit): Boolean {
        var url: String? = start
        var pages = 0
        while (url != null && pages < maxPages) {
            val root = getJson(url)
            if (root == null) return pages > 0
            onPage(root)
            url = root.optString("next").takeIf { it.isNotBlank() && it != "null" }
            pages++
        }
        return true
    }

    // ---------- Library ----------

    suspend fun fetchPlaylists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        if (tokenStore.spotifyAccessToken == null) return@withContext emptyList()
        val out = ArrayList<SpotifyPlaylist>()
        val ok = paged("https://api.spotify.com/v1/me/playlists?limit=50", 10) { root ->
            val items = root.optJSONArray("items") ?: return@paged
            for (i in 0 until items.length()) {
                val p = items.optJSONObject(i) ?: continue
                val images = p.optJSONArray("images")
                val img = if (images != null && images.length() > 0)
                    images.optJSONObject(0)?.optString("url") else null
                // Feb 2026: `tracks` was renamed to `items` on playlist objects.
                val counts = p.optJSONObject("items") ?: p.optJSONObject("tracks")
                out += SpotifyPlaylist(
                    id = p.optString("id"),
                    name = p.optString("name"),
                    imageUrl = img?.ifBlank { null },
                    trackCount = counts?.optInt("total") ?: 0,
                    uri = p.optString("uri"),
                )
            }
        }
        if (!ok && out.isEmpty()) return@withContext cache
        cache = out
        out
    }

    /**
     * Songs in one playlist. Uses the new /items endpoint. Spotify only returns contents
     * for playlists the user owns or collaborates on; others come back empty.
     */
    suspend fun fetchPlaylistTracks(playlistId: String): List<PlaylistTrack> = withContext(Dispatchers.IO) {
        val out = ArrayList<PlaylistTrack>()
        paged("https://api.spotify.com/v1/playlists/$playlistId/items?limit=50", 20) { root ->
            val items = root.optJSONArray("items") ?: return@paged
            for (i in 0 until items.length()) {
                val wrap = items.optJSONObject(i) ?: continue
                val t = wrap.optJSONObject("item") ?: wrap.optJSONObject("track") ?: continue
                t.toPlaylistTrack()?.let { out += it }
            }
        }
        out
    }

    /** Liked Songs, newest first. */
    suspend fun fetchLikedTracks(max: Int = 300): List<PlaylistTrack> = withContext(Dispatchers.IO) {
        val out = ArrayList<PlaylistTrack>()
        paged("https://api.spotify.com/v1/me/tracks?limit=50", (max + 49) / 50) { root ->
            val items = root.optJSONArray("items") ?: return@paged
            for (i in 0 until items.length()) {
                val wrap = items.optJSONObject(i) ?: continue
                val t = wrap.optJSONObject("track") ?: wrap.optJSONObject("item") ?: continue
                t.toPlaylistTrack()?.let { out += it }
            }
        }
        out.take(max)
    }

    /** How many Liked Songs you have (one tiny request). */
    suspend fun fetchLikedCount(): Int = withContext(Dispatchers.IO) {
        getJson("https://api.spotify.com/v1/me/tracks?limit=1")?.optInt("total") ?: 0
    }

    /** Your last 50 plays, most recent first, without repeats. Needs user-read-recently-played. */
    suspend fun fetchRecentlyPlayed(): List<PlaylistTrack> = withContext(Dispatchers.IO) {
        val items = getJson("https://api.spotify.com/v1/me/player/recently-played?limit=50")
            ?.optJSONArray("items") ?: return@withContext emptyList()
        val seen = HashSet<String>()
        buildList {
            for (i in 0 until items.length()) {
                val t = items.optJSONObject(i)?.optJSONObject("track")?.toPlaylistTrack() ?: continue
                if (seen.add(t.uri)) add(t)
            }
        }
    }

    /** Top tracks. timeRange: short_term (~4 weeks), medium_term (~6 months), long_term (all time). */
    suspend fun fetchTopTracks(timeRange: String = "medium_term", limit: Int = 50): List<PlaylistTrack> =
        withContext(Dispatchers.IO) {
            val items = getJson("https://api.spotify.com/v1/me/top/tracks?limit=$limit&time_range=$timeRange")
                ?.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) items.optJSONObject(i)?.toPlaylistTrack()?.let { add(it) }
            }
        }

    /** Cover art for songs that came without it, one small lookup each. */
    suspend fun trackArt(uris: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        buildMap {
            for (u in uris) {
                if (!u.startsWith("spotify:track:")) continue
                getJson("https://api.spotify.com/v1/tracks/${u.removePrefix("spotify:track:")}")?.toPlaylistTrack()?.imageUrl?.let { put(u, it) }
            }
        }
    }

    // ---------- Search ----------

    /** Song search, 10 per page (works with Spotify's tighter 2026 limits). */
    suspend fun searchTracks(query: String, offset: Int = 0, limit: Int = SEARCH_PAGE): List<PlaylistTrack> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val items = getJson("https://api.spotify.com/v1/search?type=track&limit=$limit&offset=$offset&q=$q")
                ?.optJSONObject("tracks")?.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) items.optJSONObject(i)?.toPlaylistTrack()?.let { add(it) }
            }
        }

    /** Finds the Spotify version of a song by artist + title (used for new recommendations). */
    suspend fun findTrack(artist: String, title: String): PlaylistTrack? {
        val strict = searchTracks("track:\"$title\" artist:\"$artist\"", limit = 3)
        val pick = strict.firstOrNull { it.artist.equals(artist, ignoreCase = true) } ?: strict.firstOrNull()
        return pick ?: searchTracks("$artist $title", limit = 3)
            .firstOrNull { it.artist.equals(artist, ignoreCase = true) }
    }

    suspend fun importLibrary(onProgress: (Int) -> Unit = {}): ImportResult = withContext(Dispatchers.IO) {
        val tracks = ArrayList<SpotifyTrack>()
        paged("https://api.spotify.com/v1/me/tracks?limit=50", 40) { root ->
            val items = root.optJSONArray("items") ?: return@paged
            for (i in 0 until items.length()) {
                val wrap = items.optJSONObject(i) ?: continue
                val t = wrap.optJSONObject("track") ?: wrap.optJSONObject("item") ?: continue
                tracks += t.toSpotifyTrack()
            }
            onProgress(tracks.size)
        }
        var matched = 0
        for (st in tracks) {
            val r = matcher.match(st)
            dao.upsertMatch(SpotifyMatchEntity(
                spotifyId = st.id, spotifyTitle = st.title, spotifyArtist = st.artist,
                localTrackId = r.localTrackId, confidence = r.confidence, method = r.method,
            ))
            if (r.localTrackId != null) matched++
        }
        ImportResult(tracks.size, matched, tracks.size - matched)
    }

    companion object {
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val SEARCH_PAGE = 10
    }
}

private fun JSONObject.firstArtist(): String {
    val artists = optJSONArray("artists")
    return if (artists != null && artists.length() > 0) artists.optJSONObject(0)?.optString("name").orEmpty() else ""
}

internal fun JSONObject.toPlaylistTrack(): PlaylistTrack? {
    val uri = optString("uri")
    if (!uri.startsWith("spotify:track:")) return null
    val images = optJSONObject("album")?.optJSONArray("images")
    // Smallest image (last) is plenty for row thumbnails.
    val img = if (images != null && images.length() > 0)
        images.optJSONObject(images.length() - 1)?.optString("url") else null
    return PlaylistTrack(uri, optString("name"), firstArtist(), img?.ifBlank { null }, optLong("duration_ms"))
}

private fun JSONObject.toSpotifyTrack(): SpotifyTrack {
    val album = optJSONObject("album")?.optString("name").orEmpty()
    val isrc = optJSONObject("external_ids")?.optString("isrc")?.ifBlank { null }
    return SpotifyTrack(
        id = optString("id"), title = optString("name"), artist = firstArtist(),
        album = album, durationMs = optLong("duration_ms"), isrc = isrc,
    )
}
