package com.fidelis.player.integration.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Your most-played artists, for the Heavy rotation row. */
@Singleton
class HomeFeed @Inject constructor(private val repo: SpotifyRepository) {

    /** Top artists; [range] is short_term (4 weeks), medium_term (6 months) or long_term. */
    suspend fun topArtists(range: String = "medium_term", limit: Int = 12): List<ArtistItem> =
        withContext(Dispatchers.IO) {
            val root = repo.getJson("https://api.spotify.com/v1/me/top/artists?time_range=$range&limit=$limit")
                ?: return@withContext emptyList()
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { i ->
                val a = items.optJSONObject(i) ?: return@mapNotNull null
                val id = a.optString("id")
                if (id.isBlank()) return@mapNotNull null
                val imgs = a.optJSONArray("images")
                val img = imgs?.let { (it.optJSONObject(1) ?: it.optJSONObject(0))?.optString("url") }?.ifBlank { null }
                ArtistItem(id, a.optString("name"), img, a.optString("uri"))
            }
        }
}
