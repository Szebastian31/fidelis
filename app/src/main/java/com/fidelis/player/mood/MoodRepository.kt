package com.fidelis.player.mood

import com.fidelis.player.integration.lastfm.LastfmApiImpl
import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync. Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class Mood(val label: String, val tags: Set<String>, val avoid: Set<String> = emptySet()) {
    CHILL("Chill",
        setOf("chill", "chillout", "mellow", "calm", "relaxing", "ambient", "downtempo", "acoustic",
            "lo-fi", "lofi", "chillhop", "dream pop", "soft", "trip-hop"),
        setOf("metal", "hardcore", "punk", "thrash metal", "hard rock")),
    ENERGETIC("Energetic",
        setOf("energetic", "upbeat", "high energy", "workout", "dance", "edm", "drum and bass", "rock",
            "punk", "power", "hype", "electro", "big beat"),
        setOf("ambient", "sad", "slow", "calm", "sleep")),
    FOCUS("Focus",
        setOf("instrumental", "ambient", "minimal", "post-rock", "study", "lo-fi", "lofi", "classical",
            "piano", "electronic", "downtempo", "soundtrack", "idm"),
        setOf("rap", "hip-hop", "party", "pop punk")),
    PARTY("Party",
        setOf("party", "dance", "club", "house", "deep house", "tech house", "disco", "nu disco", "funk",
            "hip-hop", "rnb", "pop", "electro", "afro house"),
        setOf("sad", "ambient", "sleep", "folk", "acoustic")),
    HAPPY("Happy",
        setOf("happy", "feel good", "feelgood", "upbeat", "summer", "sunny", "fun", "pop", "indie pop",
            "funk", "disco"),
        setOf("sad", "melancholy", "depressing", "dark")),
    MELANCHOLY("Melancholy",
        setOf("sad", "melancholy", "melancholic", "moody", "emotional", "heartbreak", "slowcore", "sadcore",
            "dark", "indie folk", "singer-songwriter"),
        setOf("party", "happy", "dance", "club")),
}

/** Where a mix draws its songs from. */
enum class Source(val label: String) {
    EVERYTHING("Everything"),
    LIKED("Liked Songs"),
    RECENT("Recently played"),
    TOP_MONTH("Top · 4 weeks"),
    TOP_HALF("Top · 6 months"),
    TOP_ALL("Top · all time"),
}

data class MoodTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val score: Int,
    val matched: List<String>,
    val imageUrl: String? = null,
    val isNew: Boolean = false, // a discovery: not in your library
)

data class MixProgress(val phase: String, val done: Int, val total: Int)

sealed class Suggestion(val title: String, val subtitle: String) {
    data object OnRepeat : Suggestion("On repeat", "Your most-played, last 4 weeks")
    data object HalfYear : Suggestion("Six-month rotation", "What you've lived in lately")
    data object Rediscover : Suggestion("Forgotten favourites", "Liked songs you haven't played in a while")
    class MoodMix(val mood: Mood, title: String, subtitle: String) : Suggestion(title, subtitle)

    companion object {
        val presets: List<Suggestion> = listOf(
            OnRepeat,
            MoodMix(Mood.CHILL, "Chill afternoon vibes", "Mellow picks from your library"),
            MoodMix(Mood.ENERGETIC, "Morning energy boost", "Upbeat songs to start the day"),
            MoodMix(Mood.PARTY, "Night out", "Dance-floor picks you already love"),
            MoodMix(Mood.FOCUS, "Deep focus", "Instrumental and minimal"),
            HalfYear,
            Rediscover,
        )
    }
}

/** Mixes that blend your songs with new ones. [mood] null = no mood filter. */
data class DiscoverPreset(val mood: Mood?, val title: String, val subtitle: String) {
    companion object {
        val presets = listOf(
            DiscoverPreset(null, "Fresh finds", "Your favourites + new songs like them"),
            DiscoverPreset(Mood.CHILL, "Chill discoveries", "Mellow, with new artists mixed in"),
            DiscoverPreset(Mood.ENERGETIC, "New energy", "Upbeat songs you haven't heard yet"),
            DiscoverPreset(Mood.PARTY, "Party, expanded", "Your dance picks + fresh ones"),
            DiscoverPreset(Mood.MELANCHOLY, "Late-night discoveries", "Moody, with new finds"),
        )
    }
}

@Singleton
class MoodRepository @Inject constructor(
    private val spotify: SpotifyRepository,
    private val lastfm: LastfmRepository,
    private val lastfmApi: LastfmApiImpl,
    private val remote: SpotifyRemote,
    private val cache: TagCache,
) {
    private val _progress = MutableStateFlow<MixProgress?>(null)
    val progress: StateFlow<MixProgress?> = _progress.asStateFlow()

    suspend fun build(source: Source, mood: Mood, limit: Int = 40): List<MoodTrack> =
        scoreTracks(tracksFor(source), mood, limit)

    suspend fun buildFromPlaylist(playlistId: String, mood: Mood, limit: Int = 40): List<MoodTrack> =
        scoreTracks(spotify.fetchPlaylistTracks(playlistId).take(200), mood, limit)

    suspend fun topMix(timeRange: String): List<MoodTrack> =
        spotify.fetchTopTracks(timeRange).map { it.toMood() }

    /** Liked songs that don't show up in your recent or top listening. */
    suspend fun rediscover(limit: Int = 40): List<MoodTrack> = withContext(Dispatchers.IO) {
        coroutineScope {
            val liked = async { spotify.fetchLikedTracks(500) }
            val recent = async { spotify.fetchRecentlyPlayed() }
            val month = async { spotify.fetchTopTracks("short_term") }
            val half = async { spotify.fetchTopTracks("medium_term") }
            val fresh = (recent.await() + month.await() + half.await()).map { it.uri }.toSet()
            liked.await().drop(30) // skip what you liked most recently
                .filter { it.uri !in fresh }
                .shuffled()
                .capPerArtist(2) { it.artist }
                .take(limit)
                .map { it.toMood() }
        }
    }

    /**
     * Your songs blended with new ones. [base] = songs to start from (a playlist);
     * null uses your whole library. About 2 of yours for every new song.
     */
    suspend fun discover(mood: Mood?, base: List<PlaylistTrack>? = null, limit: Int = 36): List<MoodTrack> =
        withContext(Dispatchers.IO) {
            val library = tracksFor(Source.EVERYTHING)
            val pool = base ?: library
            if (pool.isEmpty()) return@withContext emptyList()
            val known = (library + pool).map { key(it.artist, it.title) }.toHashSet()
            val knownUris = (library + pool).map { it.uri }.toHashSet()
            val ownTarget = limit * 2 / 3

            // 1) Your songs that fit.
            val own = if (mood != null) scoreTracks(pool, mood, ownTarget)
                      else pool.shuffled().capPerArtist(2) { it.artist }.take(ownTarget).map { it.toMood() }
            if (own.isEmpty()) return@withContext emptyList()

            try {
                // 2) Songs similar to your strongest picks, ranked by how many seeds suggest them.
                val seeds = own.take(10)
                val similar = fetchAll("Finding new songs", seeds) { s ->
                    lastfmApi.getSimilar(s.artist, cleanTitle(s.title), 25)
                }
                val counts = HashMap<String, Pair<Pair<String, String>, Int>>()
                similar.values.flatten().forEach { (a, t) ->
                    val k = key(a, t)
                    if (k in known) return@forEach
                    val prev = counts[k]
                    counts[k] = (a to t) to ((prev?.second ?: 0) + 1)
                }
                var candidates: List<Pair<String, String>> = counts.values
                    .sortedByDescending { it.second }.map { it.first }
                    .distinctBy { it.first.lowercase() } // one song per new artist
                    .take(limit)

                // 3) Keep candidates whose artist fits the mood.
                if (mood != null) {
                    val tags = fetchAll("Checking the mood", candidates.map { it.first }.distinct()) { artistTags(it) }
                    candidates = candidates.filter { score(tags[it.first].orEmpty(), mood) > 0 }
                }

                // 4) Find them on Spotify.
                val need = limit - own.size
                val found = fetchAll("Matching on Spotify", candidates.take(need + 6), permits = 3) { (a, t) ->
                    listOfNotNull(spotify.findTrack(a, t))
                }
                val fresh = candidates.mapNotNull { found[it]?.firstOrNull() }
                    .filter { it.uri !in knownUris }
                    .distinctBy { it.uri }
                    .take(need)
                    .map { MoodTrack(it.uri, it.title, it.artist, 0, listOf("new for you"), it.imageUrl, isNew = true) }

                // 5) Blend: two of yours, then one new.
                val out = ArrayList<MoodTrack>()
                var i = 0; var j = 0
                while (out.size < limit && (i < own.size || j < fresh.size)) {
                    repeat(2) { if (i < own.size) out += own[i++] }
                    if (j < fresh.size) out += fresh[j++]
                }
                out
            } finally {
                cache.save()
                _progress.value = null
            }
        }

    /**
     * A mix for a spot on the mood dial. [share] is how much of it is new to you, from 0 to about 0.6.
     * Your songs that fit come first, then songs Last.fm finds similar to them, spread evenly through.
     */
    suspend fun vibe(v: Vibe, share: Float, limit: Int = 36): List<MoodTrack> = withContext(Dispatchers.IO) {
        val library = tracksFor(Source.EVERYTHING)
        if (library.isEmpty()) return@withContext emptyList()
        try {
            val ownTarget = (limit * (1 - share)).toInt().coerceAtLeast(8)
            // 1) Your songs: a cheap pass on artist tags, then song tags for the best fits.
            val artists = library.map { it.artist }.filter { it.isNotBlank() }.distinct()
            val byArtist = fetchAll("Reading your artists", artists) { artistTags(it) }
            val shortlist = library.map { it to v.score(byArtist[it.artist].orEmpty()) }
                .filter { it.second > 2f }.sortedByDescending { it.second }.map { it.first }
                .take(ownTarget + 24)
            val byTrack = fetchAll("Reading your songs", shortlist) { trackTags(it.artist, it.title) }
            val tagByUri = HashMap<String, Set<String>>()
            val own = shortlist.map { t ->
                val tags = byTrack[t].orEmpty()
                tagByUri[t.uri] = tags.toSet()
                MoodTrack(t.uri, t.title, t.artist, v.score(tags).toInt(), tags.filter { it in v.tagWeights }.take(3), t.imageUrl)
            }.filter { it.score > 2 }.sortedByDescending { it.score }.capPerArtist(2) { it.artist }.take(ownTarget)
            if (own.isEmpty()) return@withContext emptyList()
            val mine = flowOrder(own, tagByUri)
            if (share <= 0f) return@withContext mine

            // 2) New songs like your best fits, kept when their artist suits this spot.
            val known = library.map { key(it.artist, it.title) }.toHashSet()
            val knownUris = library.map { it.uri }.toHashSet()
            val similar = fetchAll("Finding new songs", own.take(10)) { s ->
                lastfmApi.getSimilar(s.artist, cleanTitle(s.title), 25)
            }
            val counts = HashMap<String, Pair<Pair<String, String>, Int>>()
            similar.values.flatten().forEach { (a, t) ->
                val k = key(a, t)
                if (k in known) return@forEach
                val prev = counts[k]
                counts[k] = (a to t) to ((prev?.second ?: 0) + 1)
            }
            val ranked = counts.values.sortedByDescending { it.second }.map { it.first }
                .distinctBy { it.first.lowercase() }.take(limit)
            val tags = fetchAll("Checking the mood", ranked.map { it.first }.distinct()) { artistTags(it) }
            val candidates = ranked.filter { v.score(tags[it.first].orEmpty()) > 2f }
            val need = limit - mine.size
            val found = fetchAll("Matching on Spotify", candidates.take(need + 6), permits = 3) { (a, t) ->
                listOfNotNull(spotify.findTrack(a, t))
            }
            val fresh = candidates.mapNotNull { found[it]?.firstOrNull() }
                .filter { it.uri !in knownUris }
                .distinctBy { it.uri }
                .take(need)
                .map { MoodTrack(it.uri, it.title, it.artist, 0, listOf("new for you"), it.imageUrl, isNew = true) }

            // 3) Spread the new songs evenly through yours.
            val total = minOf(limit, mine.size + fresh.size)
            val every = (mine.size + fresh.size).toFloat() / fresh.size.coerceAtLeast(1)
            val out = ArrayList<MoodTrack>()
            var i = 0; var j = 0
            while (out.size < total && (i < mine.size || j < fresh.size)) {
                val newTurn = j < fresh.size && (i >= mine.size || out.size + 1 >= ((j + 1) * every).toInt())
                if (newTurn) out += fresh[j++] else out += mine[i++]
            }
            out
        } finally {
            cache.save()
            _progress.value = null
        }
    }


    fun play(tracks: List<MoodTrack>, startIndex: Int = 0) =
        remote.playQueue(tracks.map { it.toQueue() }, startIndex)

    private suspend fun tracksFor(source: Source): List<PlaylistTrack> = when (source) {
        Source.LIKED -> spotify.fetchLikedTracks(300)
        Source.RECENT -> spotify.fetchRecentlyPlayed()
        Source.TOP_MONTH -> spotify.fetchTopTracks("short_term")
        Source.TOP_HALF -> spotify.fetchTopTracks("medium_term")
        Source.TOP_ALL -> spotify.fetchTopTracks("long_term")
        Source.EVERYTHING -> coroutineScope {
            // Freshest listening first, so the cap keeps what you actually play now.
            listOf(
                async { spotify.fetchRecentlyPlayed() },
                async { spotify.fetchTopTracks("short_term") },
                async { spotify.fetchTopTracks("medium_term") },
                async { spotify.fetchLikedTracks(200) },
                async { spotify.fetchTopTracks("long_term") },
            ).awaitAll().flatten().distinctBy { it.uri }.take(220)
        }
    }

    private suspend fun scoreTracks(tracks: List<PlaylistTrack>, mood: Mood, limit: Int): List<MoodTrack> =
        withContext(Dispatchers.IO) {
            if (tracks.isEmpty()) return@withContext emptyList()
            try {
                // 1) Cheap prefilter on artist tags (one lookup per artist).
                val artists = tracks.map { it.artist }.filter { it.isNotBlank() }.distinct()
                val byArtist = fetchAll("Reading artists", artists) { artistTags(it) }
                val candidates = tracks
                    .map { it to score(byArtist[it.artist].orEmpty(), mood) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .take(maxOf(limit + 20, 40))

                // 2) Precise per-song tags for the shortlist.
                val byTrack = fetchAll("Reading songs", candidates) { trackTags(it.artist, it.title) }
                val tagByUri = HashMap<String, Set<String>>()
                val scored = candidates.map { t ->
                    val tags = byTrack[t].orEmpty()
                    tagByUri[t.uri] = tags.toSet()
                    MoodTrack(t.uri, t.title, t.artist, score(tags, mood),
                        tags.filter { it in mood.tags }.take(3), t.imageUrl)
                }
                    .filter { it.score > 0 }
                    .sortedByDescending { it.score }
                    .capPerArtist(2) { it.artist }
                    .take(limit)

                flowOrder(scored, tagByUri)
            } finally {
                cache.save()
                _progress.value = null
            }
        }

    /** Strong tags (listed first by Last.fm) weigh more; clashing tags count against. */
    private fun score(tags: List<String>, mood: Mood): Int {
        var s = 0
        tags.forEachIndexed { i, t ->
            if (t in mood.tags) s += 12 - minOf(i, 11)
            if (t in mood.avoid) s -= 8
        }
        return s
    }

    /** Chain songs that share tags so the mix flows; avoid the same artist back to back. */
    private fun flowOrder(tracks: List<MoodTrack>, tagByUri: Map<String, Set<String>>): List<MoodTrack> {
        if (tracks.size <= 2) return tracks
        val rnd = Random(System.nanoTime())
        val remaining = tracks.toMutableList()
        var current = remaining.removeAt(rnd.nextInt(minOf(3, remaining.size)))
        val ordered = arrayListOf(current)
        while (remaining.isNotEmpty()) {
            val cur = tagByUri[current.uri].orEmpty()
            val next = remaining.maxBy { c ->
                val shared = tagByUri[c.uri].orEmpty().count { it in cur }
                shared * 10 - (if (c.artist == current.artist) 50 else 0) + rnd.nextInt(4)
            }
            remaining.remove(next); ordered += next; current = next
        }
        return ordered
    }

    /** Runs [fetch] a few at a time (polite to Last.fm/Spotify) and reports progress. */
    private suspend fun <K, V> fetchAll(
        phase: String, keys: List<K>, permits: Int = 4, fetch: suspend (K) -> List<V>,
    ): Map<K, List<V>> = coroutineScope {
        val sem = Semaphore(permits)
        val done = AtomicInteger()
        _progress.value = MixProgress(phase, 0, keys.size)
        keys.map { k ->
            async {
                val r = sem.withPermit { runCatching { fetch(k) }.getOrDefault(emptyList()) }
                val n = done.incrementAndGet()
                _progress.update { it?.copy(done = n) }
                k to r
            }
        }.awaitAll().toMap()
    }

    private suspend fun artistTags(artist: String): List<String> {
        val key = "a|${artist.lowercase()}"
        cache.get(key)?.let { return it }
        return lastfm.artistTags(artist).also { cache.put(key, it) }
    }

    private suspend fun trackTags(artist: String, title: String): List<String> {
        val clean = cleanTitle(title)
        val key = "t|${artist.lowercase()}|${clean.lowercase()}"
        val tags = cache.get(key) ?: lastfm.trackTags(artist, clean).also { cache.put(key, it) }
        return tags.ifEmpty { artistTags(artist) }
    }

    private fun key(artist: String, title: String) =
        "${artist.lowercase().trim()}|${cleanTitle(title).lowercase().trim()}"

    // "Song - Remastered 2011" / "Song (feat. X)" → "Song". Remixes are kept: they tag differently.
    private fun cleanTitle(t: String): String = t.replace(
        Regex("""\s*[-–(\[]\s*(\d{4}\s*)?(remaster(ed)?|radio edit|feat\.|ft\.|mono|stereo|single version).*$""",
            RegexOption.IGNORE_CASE), "",
    ).ifBlank { t }
}

private fun PlaylistTrack.toMood() = MoodTrack(uri, title, artist, 0, emptyList(), imageUrl)
fun MoodTrack.toQueue() = QueueTrack(uri, title, artist, imageUrl)

private fun <T> List<T>.capPerArtist(max: Int, artist: (T) -> String): List<T> {
    val seen = HashMap<String, Int>()
    return filter {
        val n = (seen[artist(it)] ?: 0) + 1
        seen[artist(it)] = n
        n <= max
    }
}
