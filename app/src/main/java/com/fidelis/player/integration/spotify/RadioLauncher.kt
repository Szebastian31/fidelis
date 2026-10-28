package com.fidelis.player.integration.spotify

import android.content.Context
import android.widget. Toast
import com.fidelis.player.integration.lastfm.LastfmApiImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Song radio and "play similar": songs like one song (or a whole playlist), found on Last.fm
 * and matched on Spotify. Also finds an artist's page from just their name.
 */
@Singleton
class RadioLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SpotifyRepository,
    private val catalog: SpotifyCatalog,
    private val remote: SpotifyRemote,
    private val lastfm: LastfmApiImpl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** Plays the song (unless it's already on) and fills Up next with songs like it. */
    fun songRadio(title: String, artist: String) = start("Building a radio from “$title”…") {
        val now = remote.state.value
        val nowUri = now?.uri
        val playing = now != null && now.title.equals(title, true) && now.artist.equals(artist, true)
        val seed = if (playing && nowUri != null) PlaylistTrack(nowUri, title, artist)
            else repo.findTrack(artist, title) ?: return@start toast("Couldn't find that song on Spotify")
        if (!playing) remote.playQueue(listOf(seed.toQueue()))
        val similar = findSimilar(listOf(seed))
        if (similar.isEmpty()) return@start toast("Last.fm doesn't know songs like this one yet")
        remote.setUpNext(similar.map { it.toQueue() })
        toast("Radio ready · ${similar.size} songs")
    }

    /** Plays songs like these (a playlist, Liked Songs…), leaving out the ones already in it. */
    fun similarTo(tracks: List<PlaylistTrack>, label: String) = start("Finding songs like $label…") {
        if (tracks.isEmpty()) return@start
        val found = findSimilar(tracks.shuffled().take(8), exclude = tracks).shuffled()
        if (found.isEmpty()) return@start toast("Last.fm doesn't know songs like these yet")
        remote.playQueue(found.map { it.toQueue() })
        toast("Playing ${found.size} songs like $label")
    }

    /** Spotify id of an artist, from just their name. */
    suspend fun artistId(name: String): String? = runCatching {
        val found = catalog.search(name).artists
        (found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull())?.id
    }.getOrNull()

    private suspend fun findSimilar(seeds: List<PlaylistTrack>, exclude: List<PlaylistTrack> = seeds): List<PlaylistTrack> =
        withContext(Dispatchers.IO) {
            fun key(a: String, t: String) = "${a.lowercase()}|${t.lowercase()}"
            val skip = exclude.mapTo(HashSet()) { key(it.artist, it.title) }
            val skipUris = exclude.mapTo(HashSet()) { it.uri }
            val perSeed = if (seeds.size == 1) 60 else 25
            val similar = coroutineScope {
                seeds.map { s -> async { runCatching { lastfm.getSimilar(s.artist, s.title, perSeed) }.getOrDefault(emptyList()) } }
                    .awaitAll()
            }.flatten()

            // The more seeds agree on a song, the higher it ranks; ties keep Last.fm's order.
            val counts = LinkedHashMap<String, Int>()
            val byKey = HashMap<String, Pair<String, String>>()
            similar.forEach { p ->
                val k = key(p.first, p.second)
                if (k in skip) return@forEach
                counts[k] = (counts[k] ?: 0) + 1
                byKey.getOrPut(k) { p }
            }
            val perArtist = HashMap<String, Int>()
            val picks = counts.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }
                .filter { p ->
                    val n = (perArtist[p.first.lowercase()] ?: 0) + 1
                    perArtist[p.first.lowercase()] = n
                    n <= 2
                }
                .take(SIZE + 8)

            val sem = Semaphore(4)
            coroutineScope {
                picks.map { p -> async { sem.withPermit { runCatching { repo.findTrack(p.first, p.second) }.getOrNull() } } }
                    .awaitAll()
            }.filterNotNull().filterNot { it.uri in skipUris }.distinctBy { it.uri }.take(SIZE)
        }

    private fun start(message: String, block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        toast(message)
        job = scope.launch { runCatching { block() } }
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    private companion object { const val SIZE = 30 }
}
