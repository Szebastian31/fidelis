package com.fidelis.player.integration.spotify

import com.fidelis.player.integration.lastfm.LastfmApiImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Artist radio: mostly the artist, with songs in a similar vibe mixed in (two of theirs, then
 * one similar). Similar songs come from Last.fm and are matched on Spotify.
 */
@Singleton
class ArtistRadio @Inject constructor(
    private val repo: SpotifyRepository,
    private val lastfm: LastfmApiImpl,
) {
    suspend fun build(artist: String, own: List<PlaylistTrack>, size: Int = 40): List<PlaylistTrack> =
        withContext(Dispatchers.IO) {
            val mine = own.distinctBy { it.title.lowercase() }.shuffled().take(size * 2 / 3)
            if (mine.isEmpty()) return@withContext emptyList()

            // Songs similar to a few of theirs; the more seeds agree, the higher it ranks.
            val similar = coroutineScope {
                mine.take(6).map { s ->
                    async { runCatching { lastfm.getSimilar(s.artist, s.title, 20) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }.flatten().filterNot { it.first.equals(artist, ignoreCase = true) }

            val counts = HashMap<String, Int>()
            val byKey = HashMap<String, Pair<String, String>>()
            similar.forEach { p ->
                val k = "${p.first.lowercase()}|${p.second.lowercase()}"
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
                .take(size - mine.size + 6)

            val sem = Semaphore(3)
            val found = coroutineScope {
                picks.map { p -> async { sem.withPermit { runCatching { repo.findTrack(p.first, p.second) }.getOrNull() } } }
                    .awaitAll()
            }.filterNotNull()
                .filterNot { it.artist.equals(artist, ignoreCase = true) }
                .distinctBy { it.uri }
                .take(size - mine.size)

            // Blend: two of theirs, then one similar.
            val out = ArrayList<PlaylistTrack>()
            var i = 0; var j = 0
            while (i < mine.size || j < found.size) {
                repeat(2) { if (i < mine.size) out += mine[i++] }
                if (j < found.size) out += found[j++]
            }
            out
        }
}
