package com.fidelis.player.integration.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistItem(val id: String, val name: String, val imageUrl: String?, val uri: String)

data class AlbumItem(
    val id: String, val name: String, val artist: String, val artistId: String?,
    val imageUrl: String?, val year: String, val type: String, val uri: String,
) {
    /** "Album · 2021", "Single · 2024". */
    val label: String get() =
        listOf(type.replaceFirstChar { it.uppercase() }, year).filter { it.isNotBlank() }.joinToString(" · ")
    val isSingle get() = type.equals("single", ignoreCase = true)
}

/** Anyone's playlist from search. Spotify only lists the songs of your own, so the rest play as a whole. */
data class PlaylistItem(
    val id: String, val name: String, val owner: String, val ownerId: String,
    val imageUrl: String?, val total: Int, val uri: String,
) {
    /** "By Spotify \u00B7 50 songs". */
    val label: String get() = listOfNotNull(
        owner.takeIf { it.isNotBlank() }?.let { "By $it" },
        total.takeIf { it > 0 }?.let { "$it songs" },
    ).joinToString(" \u00B7 ")
}

data class SearchResults(
    val artists: List<ArtistItem>, val albums: List<AlbumItem>, val tracks: List<PlaylistTrack>,
    val playlists: List<PlaylistItem> = emptyList(),
) {
    val isEmpty get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty() && playlists.isEmpty()
}

data class AlbumPage(val album: AlbumItem, val tracks: List<PlaylistTrack>)

/**
 * Catalog lookups for search, artist and album pages, within Spotify's 2026 rules for
 * Development Mode apps: at most 10 results per search page and no artist top-tracks list.
 */
@Singleton
class SpotifyCatalog @Inject constructor(private val repo: SpotifyRepository) {

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val empty = SearchResults(emptyList(), emptyList(), emptyList())
        if (query.isBlank()) return@withContext empty
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val root = repo.getJson("$API/search?type=artist,album,track,playlist&limit=10&q=$q") ?: return@withContext empty
        SearchResults(
            artists = root.optJSONObject("artists")?.optJSONArray("items").objects()
                .mapNotNull { it.toArtist() }.distinctBy { it.id },
            albums = root.optJSONObject("albums")?.optJSONArray("items").objects()
                .mapNotNull { it.toAlbum() }.distinctBy { it.id },
            tracks = root.optJSONObject("tracks")?.optJSONArray("items").objects()
                .mapNotNull { it.toPlaylistTrack() }.distinctBy { it.uri },
            playlists = root.optJSONObject("playlists")?.optJSONArray("items").objects()
                .mapNotNull { it.toPlaylist() }.distinctBy { it.id },
        )
    }

    /** The next 10 playlists, for "More playlists". */
    suspend fun morePlaylists(query: String, offset: Int): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        runCatching {
            repo.getJson("$API/search?type=playlist&limit=10&offset=$offset&q=$q")
                ?.optJSONObject("playlists")?.optJSONArray("items").objects().mapNotNull { it.toPlaylist() }
        }.getOrNull().orEmpty()
    }

    /** A playlist's name, owner and cover, for a pasted link. */
    suspend fun playlist(id: String): PlaylistItem? = withContext(Dispatchers.IO) {
        runCatching { repo.getJson("$API/playlists/$id")?.toPlaylist() }.getOrNull()
    }

    suspend fun artist(id: String): ArtistItem? = withContext(Dispatchers.IO) {
        repo.getJson("$API/artists/$id")?.toArtist()
    }

    /** Albums and singles, newest first, up to 50. */
    suspend fun artistAlbums(id: String): List<AlbumItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<AlbumItem>()
        for (page in 0 until 5) {
            val root = repo.getJson("$API/artists/$id/albums?include_groups=album,single&limit=10&offset=${page * 10}") ?: break
            out += root.optJSONArray("items").objects().mapNotNull { it.toAlbum() }
            if (root.isNull("next")) break
        }
        out.distinctBy { "${it.type}|${it.name.lowercase()}" }.sortedByDescending { it.year }
    }

    /** Spotify no longer offers an artist's top songs, so search their name instead. */
    suspend fun artistSongs(name: String): List<PlaylistTrack> {
        val q = "artist:\"$name\""
        val first = repo.searchTracks(q)
        val second = if (first.size >= SpotifyRepository.SEARCH_PAGE)
            repo.searchTracks(q, offset = SpotifyRepository.SEARCH_PAGE) else emptyList()
        return (first + second).filter { it.artist.equals(name, ignoreCase = true) }
            .distinctBy { it.title.lowercase() }
    }

    /** The album with every song in its own order (disc, then track number), any length. */
    suspend fun album(id: String): AlbumPage? = withContext(Dispatchers.IO) {
        val root = repo.getJson("$API/albums/$id") ?: return@withContext null
        val album = root.toAlbum() ?: return@withContext null
        val raw = ArrayList<JSONObject>()
        var page: JSONObject? = root.optJSONObject("tracks")
        var guard = 0
        while (page != null && guard++ < 30) {
            val p = page
            raw += p.optJSONArray("items").objects()
            val next = if (p.isNull("next")) "" else p.optString("next")
            page = if (next.isBlank()) null else repo.getJson(next)
        }
        // Album tracks carry no cover of their own; use the album's.
        val tracks = raw
            .sortedWith(compareBy({ it.optInt("disc_number", 1) }, { it.optInt("track_number", 0) }))
            .mapNotNull { t ->
                val uri = t.optString("uri")
                if (!uri.startsWith("spotify:track:")) null
                else PlaylistTrack(uri, t.optString("name"), t.leadArtist().ifBlank { album.artist }, album.imageUrl, t.optLong("duration_ms"))
            }.distinctBy { it.uri }
        AlbumPage(album, tracks)
    }

    private companion object { const val API = "https://api.spotify.com/v1" }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

// Spotify lists images largest first; the middle size (~300px) suits tiles.
private fun JSONObject.image(): String? {
    val imgs = optJSONArray("images").objects()
    return (imgs.getOrNull(1) ?: imgs.firstOrNull())?.optString("url")?.ifBlank { null }
}

private fun JSONObject.leadArtist(): String =
    optJSONArray("artists").objects().firstOrNull()?.optString("name").orEmpty()

// org.json turns a JSON null into the text "null".
private fun JSONObject.text(name: String): String = optString(name).takeUnless { it == "null" }.orEmpty()

private fun JSONObject.toPlaylist(): PlaylistItem? {
    val id = text("id")
    if (id.isBlank()) return null
    val owner = optJSONObject("owner")
    // Newer responses call the song count "items", older ones "tracks".
    val total = (optJSONObject("items") ?: optJSONObject("tracks"))?.optInt("total") ?: 0
    return PlaylistItem(
        id = id, name = text("name"),
        owner = owner?.text("display_name").orEmpty(), ownerId = owner?.text("id").orEmpty(),
        imageUrl = image(), total = total, uri = text("uri").ifBlank { "spotify:playlist:$id" },
    )
}

private fun JSONObject.toArtist(): ArtistItem? {
    val id = optString("id")
    if (id.isBlank()) return null
    return ArtistItem(id, optString("name"), image(), optString("uri"))
}

private fun JSONObject.toAlbum(): AlbumItem? {
    val id = optString("id")
    if (id.isBlank()) return null
    val lead = optJSONArray("artists").objects().firstOrNull()
    return AlbumItem(
        id = id, name = optString("name"),
        artist = lead?.optString("name").orEmpty(), artistId = lead?.optString("id")?.ifBlank { null },
        imageUrl = image(), year = optString("release_date").take(4),
        type = optString("album_type"), uri = optString("uri"),
    )
}
