package com.fidelis.player.mood

import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.QueueTrack
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

sealed class Suggestion(val title: String, val subtitle: String) {
    data object OnRepeat : Suggestion("On repeat lately", "Your most-played, in order")
    class MoodMix(val mood: Mood, title: String, subtitle: String) : Suggestion(title, subtitle)

    companion object {
        val presets: List<Suggestion> = listOf(
            OnRepeat,
            MoodMix(Mood.CHILL, "Chill afternoon vibes", "Mellow picks from your rotation"),
            MoodMix(Mood.ENERGETIC, "Morning energy boost", "High-energy from your top tracks"),
            MoodMix(Mood.FOCUS, "Deep focus", "Instrumental & minimal"),
            MoodMix(Mood.PARTY, "Night out", "Danceable & loud"),
        )
    }
}

@Singleton
class MoodRepository @Inject constructor(
    private val spotify: SpotifyRepository,
    private val lastfm: LastfmRepository,
    private val remote: SpotifyRemote,
) {
    private val tagCache = ConcurrentHashMap<String, List<String>>()

    suspend fun build(playlistId: String, mood: Mood, limit: Int = 40): List<MoodTrack> =
        scoreTracks(spotify.fetchPlaylistTracks(playlistId).take(80), mood, limit)

    suspend fun moodFromTop(mood: Mood, timeRange: String = "medium_term", limit: Int = 40): List<MoodTrack> =
        scoreTracks(spotify.fetchTopTracks(timeRange), mood, limit)

    suspend fun topMix(timeRange: String = "medium_term"): List<MoodTrack> = withContext(Dispatchers.IO) {
        spotify.fetchTopTracks(timeRange).map { MoodTrack(it.uri, it.title, it.artist, 0, emptyList()) }
    }

    fun play(tracks: List<MoodTrack>) =
        remote.playQueue(tracks.map { QueueTrack(it.uri, it.title, it.artist) })

    private suspend fun scoreTracks(tracks: List<PlaylistTrack>, mood: Mood, limit: Int): List<MoodTrack> =
        withContext(Dispatchers.IO) {
            tracks.map { t ->
                val matched = tagsFor(t.artist).filter { it in mood.tags }
                MoodTrack(t.uri, t.title, t.artist, matched.size, matched)
            }.filter { it.score > 0 }.sortedByDescending { it.score }.take(limit)
        }

    private suspend fun tagsFor(artist: String): List<String> {
        if (artist.isBlank()) return emptyList()
        tagCache[artist]?.let { return it }
        val tags = lastfm.artistTags(artist)
        tagCache[artist] = tags
        return tags
    }
}
