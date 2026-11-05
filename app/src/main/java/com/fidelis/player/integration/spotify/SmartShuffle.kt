package com.fidelis.player.integration.spotify

import android.content.Context
import com.fidelis.player.integration.lastfm.LastfmApiImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shuffle for playlists. Off, it plays only the playlist's songs. On, songs that suit the playlist join
 * Up next a few seconds later, about one after every three of yours, found from Last.fm's similar songs.
 */
@Singleton
class SmartShuffle @Inject constructor(
    @ApplicationContext context: Context,
    private val lastfm: LastfmApiImpl,
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
) {
    private val prefs = context.getSharedPreferences("smart_shuffle", Context.MODE_PRIVATE)
    private val _on = MutableStateFlow(prefs.getBoolean("on", false))
    val on: StateFlow<Boolean> = _on.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** Remembers the choice. Turning it off takes the suggestions back out of Up next. */
    fun set(value: Boolean) {
        prefs.edit().putBoolean("on", value).apply()
        _on.value = value
        if (!value) { job?.cancel(); remote.unblend() }
    }

    /** Shuffles [tracks] and starts at once; with smart shuffle on, suggestions follow when they're found. */
    fun play(tracks: List<QueueTrack>, from: String?) {
        if (tracks.isEmpty()) return
        job?.cancel()
        val smart = _on.value
        remote.playQueue(tracks.freshShuffle { it.artist }, 0, from = if (smart) listOfNotNull(from, "Smart shuffle").joinToString(" · ") else from)
        if (!smart) return
        job = scope.launch {
            val picks = runCatching { suggest(tracks) }.getOrDefault(emptyList())
            if (_on.value) remote.blendIn(picks)
        }
    }

    private suspend fun suggest(tracks: List<QueueTrack>): List<QueueTrack> = withContext(Dispatchers.IO) {
        val have = tracks.map { key(it.artist, it.title) }.toHashSet()
        val haveUris = tracks.map { it.uri }.toHashSet()
        val seeds = tracks.shuffled().take(8)
        val similar = coroutineScope {
            seeds.map { s ->
                async { runCatching { lastfm.getSimilar(mainArtist(s.artist), cleanSong(s.title), 30) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        // Songs that several of the playlist's songs point to come first.
        val counts = HashMap<String, Pair<Pair<String, String>, Int>>()
        similar.flatten().forEach { (a, t) ->
            val k = key(a, t)
            if (k in have) return@forEach
            counts[k] = (a to t) to ((counts[k]?.second ?: 0) + 1)
        }
        val want = (tracks.size / 3).coerceIn(5, 30)
        val perArtist = HashMap<String, Int>()
        val ranked = counts.values.sortedByDescending { it.second }.map { it.first }.filter { (a, _) ->
            val n = perArtist[a.lowercase()] ?: 0
            if (n >= 2) false else { perArtist[a.lowercase()] = n + 1; true }
        }.take(want + 8)
        val found = ranked.chunked(4).flatMap { part ->
            coroutineScope { part.map { (a, t) -> async { runCatching { repo.findTrack(a, t) }.getOrNull() } }.awaitAll() }
        }
        found.filterNotNull()
            .filter { it.uri !in haveUris && key(it.artist, it.title) !in have }
            .distinctBy { it.uri }.take(want).map { it.toQueue() }.shuffled()
    }

    private fun mainArtist(a: String) = a.split(",", " & ", " feat.", " ft.").first().trim().ifBlank { a }

    private fun cleanSong(t: String) = t.substringBefore(" - ").replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim().ifBlank { t }

    private fun key(artist: String, title: String) =
        mainArtist(artist).lowercase().filter { it.isLetterOrDigit() } + "|" + cleanSong(title).lowercase().filter { it.isLetterOrDigit() }
}
