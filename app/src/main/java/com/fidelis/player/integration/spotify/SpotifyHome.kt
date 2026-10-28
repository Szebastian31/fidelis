package com.fidelis.player.integration.spotify

import android.graphics.Bitmap
import android.util.LruCache
import com.spotify.protocol.types.ListItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

data class HomeSection(val title: String, val items: List<ListItem>)

/** What Spotify's own home screen shows you: its mixes, your recents and its other shelves. */
data class SpotifyHomeFeed(val made: List<ListItem>, val recents: List<ListItem>, val sections: List<HomeSection>)

@Singleton
class SpotifyHome @Inject constructor(private val remote: SpotifyRemote) {
    private val art = LruCache<String, Bitmap>(150)

    suspend fun load(): SpotifyHomeFeed? {
        if (!remote.connected.value) return null
        val root = remote.browse(null)
        if (root.isEmpty()) return null
        val recentsRoot = root.firstOrNull { it.id.orEmpty().contains("recently-played") }
            ?: ListItem(RECENTS, RECENTS, null, "Recents", "", false, true)
        val shelves = root.filter { it.hasChildren && it.id != recentsRoot.id && it.id.orEmpty() !in SKIP }
        val (recents, loaded) = coroutineScope {
            val r = async { remote.browse(recentsRoot, 12) }
            val s = shelves.map { sh -> async { sh to remote.browse(sh, 20) } }
            r.await() to s.awaitAll()
        }
        // Spotify's personal mixes go up top; the shelves keep everything else.
        val made = loaded.flatMap { it.second }.filter(::usable)
            .filter { MADE.containsMatchIn(it.title.orEmpty()) }.distinctBy(::key).take(10)
        val madeKeys = made.mapTo(HashSet(), ::key)
        val sections = loaded.map { (sh, items) ->
            HomeSection(sh.title.orEmpty(), items.filter(::usable).filterNot { key(it) in madeKeys }.distinctBy(::key))
        }.filter { it.title.isNotBlank() && it.items.size >= 2 }
        return SpotifyHomeFeed(made, recents.filter(::usable).distinctBy(::key), sections)
    }

    fun cached(item: ListItem): Bitmap? = item.imageUri?.raw?.let { art.get(it) }

    suspend fun art(item: ListItem): Bitmap? {
        val uri = item.imageUri ?: return null
        val k = uri.raw ?: return null
        art.get(k)?.let { return it }
        return remote.image(uri)?.also { art.put(k, it) }
    }

    private fun usable(i: ListItem) = i.playable || i.uri.orEmpty().startsWith("spotify:")
    private fun key(i: ListItem): String = i.uri ?: i.id.orEmpty()

    companion object {
        const val DJ_URI = "spotify:playlist:37i9dQZF1EYkqdzj48dyYq"
        private const val RECENTS = "com.spotify.recently-played"
        private val SKIP = setOf("com.spotify.your-library", "com.spotify.your-music", "com.spotify.browse", "spotify.browse")
        private val MADE = Regex("daylist|discover weekly|release radar|daily mix|on repeat|repeat rewind|time capsule", RegexOption.IGNORE_CASE)
    }
}
