package com.fidelis.player.integration.spotify

import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.data.db.SpotifyMatchEntity
import com.fidelis.player.data.db.TrackEntity
import com.fidelis.player.util.Normalizer
import kotlin.math.abs
import kotlin.math.max

/** A track as returned by the Spotify library import. */
data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isrc: String?,
)

data class MatchResult(
    val localTrackId: Long?,
    val confidence: Int,       // 0..100
    val method: String,        // "ISRC" | "FUZZY" | "NONE"
)

/**
 * Matches an imported Spotify track to a local file.
 *
 * Strategy (strongest signal first):
 *   1. ISRC exact match  -> confidence 100, method "ISRC".
 *   2. Normalized-key bucket + weighted similarity:
 *        0.55 * titleSim + 0.30 * artistSim + 0.15 * albumSim,
 *      gated by a duration window (|Δ| <= 4s full credit, decaying to 0 by 12s).
 *      Accept the best candidate when score >= ACCEPT (0.72); otherwise it's a "gap".
 *
 * Confidence is the blended score (0..100); low-confidence matches are surfaced
 * for a one-tap confirm instead of being trusted silently.
 */
class SpotifyMatcher(private val dao: MusicDao) {

    companion object {
        private const val ACCEPT = 0.72
        private const val DURATION_FULL_MS = 4_000L
        private const val DURATION_ZERO_MS = 12_000L
    }

    suspend fun match(spotify: SpotifyTrack): MatchResult {
        // 1) ISRC — the one signal that's unambiguous when present on both sides.
        spotify.isrc?.let { isrc ->
            dao.trackByIsrc(isrc)?.let { return MatchResult(it.id, 100, "ISRC") }
        }

        // 2) Fuzzy within the normalized bucket (cheap), then fall back to a wider pass if empty.
        val key = Normalizer.key(spotify.artist, spotify.title)
        val candidates = dao.tracksByNormalizedKey(key)
            .ifEmpty { widen(spotify) }

        var best: TrackEntity? = null
        var bestScore = 0.0
        for (c in candidates) {
            val s = score(spotify, c)
            if (s > bestScore) { bestScore = s; best = c }
        }

        return if (best != null && bestScore >= ACCEPT)
            MatchResult(best.id, (bestScore * 100).toInt(), "FUZZY")
        else
            MatchResult(null, (bestScore * 100).toInt(), "NONE")
    }

    /** Run a batch and persist every outcome (matches and gaps). */
    suspend fun matchAll(spotifyTracks: List<SpotifyTrack>) {
        for (s in spotifyTracks) {
            val r = match(s)
            dao.upsertMatch(
                SpotifyMatchEntity(
                    spotifyId = s.id, spotifyTitle = s.title, spotifyArtist = s.artist,
                    localTrackId = r.localTrackId, confidence = r.confidence, method = r.method,
                )
            )
        }
    }

    // --- scoring ---

    private fun score(s: SpotifyTrack, c: TrackEntity): Double {
        val title = similarity(Normalizer.normalize(s.title), Normalizer.normalize(c.title))
        val artist = similarity(Normalizer.normalize(s.artist), Normalizer.normalize(c.artist))
        val album = similarity(Normalizer.normalize(s.album), Normalizer.normalize(c.album))
        val base = 0.55 * title + 0.30 * artist + 0.15 * album
        return base * durationGate(abs(s.durationMs - c.durationMs))
    }

    /** 1.0 within 4s, linearly decaying to 0.0 at 12s difference. */
    private fun durationGate(deltaMs: Long): Double = when {
        deltaMs <= DURATION_FULL_MS -> 1.0
        deltaMs >= DURATION_ZERO_MS -> 0.0
        else -> 1.0 - (deltaMs - DURATION_FULL_MS).toDouble() /
            (DURATION_ZERO_MS - DURATION_FULL_MS)
    }

    /** Normalized Levenshtein similarity in [0,1]. */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / max(a.length, b.length).coerceAtLeast(1)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    /** Fallback: artist-only bucket when the exact key misses (e.g. title punctuation). */
    private suspend fun widen(s: SpotifyTrack): List<TrackEntity> {
        val artistKey = Normalizer.normalize(s.artist.substringBefore(',').substringBefore('&'))
        // Reuse the title token as a loose probe; in practice back this with a FTS table.
        return dao.tracksByNormalizedKey("$artistKey|${Normalizer.normalize(s.title)}")
    }
}
