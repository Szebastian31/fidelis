package com.fidelis.player.mood

import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class Mood(val label: String, val tags: Set<String>) {
    CHILL("Chill", setOf("chill", "mellow", "calm", "ambient", "acoustic", "chillout", "relaxing", "downtempo")),
    ENERGETIC("Energetic", setOf("energetic", "upbeat", "dance", "hype", "workout", "power", "rock", "punk")),
    FOCUS("Focus", setOf("instrumental", "ambient", "minimal", "post-rock", "study", "chillout", "electronic")),
    PARTY("Party", setOf("party", "dance", "pop", "club", "electronic", "house", "hip-hop", "rnb")),
    HAPPY("Happy", setOf("happy", "feel good", "upbeat", "summer", "pop", "sunny", "indie pop")),
    MELANCHOLY("Melancholy", setOf("sad", "melancholy", "moody", "emotional", "indie", "mellow", "slowcore")),
}

data class MoodTrack(val uri: String, val title: String, val artist: String, val score: Int, val matched: List<String>)

@Singleton
class MoodRepository @Inject constructor(
    private val spotify: SpotifyRepository,
    private val lastfm: LastfmRepository,
    private val remote: SpotifyRemote,
) {
    private val tagCache = ConcurrentHashMap<String, List<String>>()

    /** Score a playlist's tracks against a mood via Last.fm artist tags, best first. */
    suspend fun build(playlistId: String, mood: Mood, limit: Int = 40): List<MoodTrack> =
        withContext(Dispatchers.IO) {
            val tracks = spotify.fetchPlaylistTracks(playlistId).take(80) // bound Last.fm calls
            tracks.map { t ->
                val matched = tagsFor(t.artist).filter { it in mood.tags }
                MoodTrack(t.uri, t.title, t.artist, matched.size, matched)
            }.filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(limit)
        }

    fun play(tracks: List<MoodTrack>) = remote.playQueue(tracks.map { it.uri })

    private suspend fun tagsFor(artist: String): List<String> {
        if (artist.isBlank()) return emptyList()
        tagCache[artist]?.let { return it }
        val tags = lastfm.artistTags(artist)
        tagCache[artist] = tags
        return tags
    }
}
