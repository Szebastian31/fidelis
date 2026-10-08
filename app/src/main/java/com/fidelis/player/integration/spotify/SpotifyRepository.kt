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
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject. Singleton

data class ImportResult(val total: Int, val matched: Int, val gaps: Int)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val uri: String,
)

// 1. NEW: used by the mood builder (needs the artist for tag lookup)
data class PlaylistTrack(val uri: String, val title: String, val artist: String)

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

    private val _connected = MutableStateFlow(tokenStore.spotifyAccessToken != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

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
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
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
        _connected.value = false
    }

    /** The user's playlists (paginated), for the Playlists tab. */
    suspend fun fetchPlaylists(): List<SpotifyPlaylist> = withContext(Dispatchers. IO) {
        val token = tokenStore.spotifyAccessToken ?: return@withContext emptyList()
        val out = ArrayList<SpotifyPlaylist>()
        var url: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        var pages = 0
        while (url != null && pages < 10) {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            url = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                val items = root.optJSONArray("items") ?: return@use null
                for (i in 0 until items.length()) {
                    val p = items.getJSONObject(i)
                    val images = p.optJSONArray("images")
                    val img = if (images != null && images.length() > 0)
                        images.getJSONObject(0).optString("url") else null
                    out += SpotifyPlaylist(
                        id = p.optString("id"),
                        name = p.optString("name"),
                        imageUrl = img?.ifBlank { null },
                        trackCount = p.optJSONObject("tracks")?.optInt("total") ?: 0,
                        uri = p.optString("uri"),
                    )
                }
                root.optString("next").takeIf { it.isNotBlank() && it != "null" }
            }
            pages++
        }
        out
    }

    // 2. REPLACES fetchPlaylistTrackUris: now returns uri + title + artist for the mood builder.
    /** Tracks for one playlist (uri + title + artist). */
    suspend fun fetchPlaylistTracks(playlistId: String): List<PlaylistTrack> = withContext(Dispatchers.IO) {
        val token = tokenStore.spotifyAccessToken ?: return@withContext emptyList()
        val out = ArrayList<PlaylistTrack>()
        var url: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
        var pages = 0
        while (url != null && pages < 10) {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            url = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                val items = root.optJSONArray("items") ?: return@use null
                for (i in 0 until items.length()) {
                    val t = items.getJSONObject(i).optJSONObject("track") ?: continue
                    val uri = t.optString("uri")
                    if (!uri.startsWith("spotify:track:")) continue
                    val artists = t.optJSONArray("artists")
                    val artist = if (artists != null && artists.length() > 0)
                        artists.getJSONObject(0).optString("name") else ""
                    out += PlaylistTrack(uri, t.optString("name"), artist)
                }
                root.optString("next").takeIf { it.isNotBlank() && it != "null" }
            }
            pages++
        }
        out
    }

    /** The user's top tracks (needs the user-top-read scope) for personalized suggestions. */
    suspend fun fetchTopTracks(timeRange: String = "medium_term", limit: Int = 50): List<PlaylistTrack> =
        withContext(Dispatchers.IO) {
            val token = tokenStore.spotifyAccessToken ?: return@withContext emptyList()
            val url = "https://api.spotify.com/v1/me/top/tracks?limit=$limit&time_range=$timeRange"
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val items = JSONObject(resp.body?.string().orEmpty()).optJSONArray("items")
                        ?: return@use emptyList()
                    buildList {
                        for (i in 0 until items.length()) {
                            val t = items.getJSONObject(i)
                            val uri = t.optString("uri")
                            if (!uri.startsWith("spotify:track:")) continue
                            val artists = t.optJSONArray("artists")
                            val artist = if (artists != null && artists.length() > 0)
                                artists.getJSONObject(0).optString("name") else ""
                            add(PlaylistTrack(uri, t.optString("name"), artist))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    
    suspend fun importLibrary(onProgress: (Int) -> Unit = {}): ImportResult = withContext(Dispatchers.IO) {
        val token = tokenStore.spotifyAccessToken ?: return@withContext ImportResult(0, 0, 0)
        val tracks = ArrayList<SpotifyTrack>()
        var url: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        var pages = 0
        while (url != null && pages < 40) {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            url = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                val items = root.optJSONArray("items") ?: return@use null
                for (i in 0 until items.length()) {
                    val t = items.getJSONObject(i).optJSONObject("track") ?: continue
                    tracks += t.toSpotifyTrack()
                }
                onProgress(tracks.size)
                root.optString("next").takeIf { it.isNotBlank() && it != "null" }
            }
            pages++
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
}

private fun JSONObject.toSpotifyTrack(): SpotifyTrack {
    val artists = optJSONArray("artists")
    val artist = if (artists != null && artists.length() > 0) artists.getJSONObject(0).optString("name") else ""
    val album = optJSONObject("album")?.optString("name").orEmpty()
    val isrc = optJSONObject("external_ids")?.optString("isrc")?.ifBlank { null }
    return SpotifyTrack(
        id = optString("id"), title = optString("name"), artist = artist,
        album = album, durationMs = optLong("duration_ms"), isrc = isrc,
    )
}
