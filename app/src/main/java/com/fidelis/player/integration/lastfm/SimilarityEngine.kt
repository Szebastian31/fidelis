package com.fidelis.player.integration.lastfm

import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.util.Normalizer
import kotlin.math.abs

/**
 * Consensus Smart Shuffle.
 *
 * Blends two independent similarity signals and reports WHICH one spoke, so a suggestion
 * can be labelled Crowd / Audio / Consensus just like the prototype:
 *   - CROWD: Last.fm track.getSimilar (what listeners who like the seed also play)
 *   - AUDIO: on-device distance in tempo / key / energy
 *
 * Only tracks the user already owns are ever suggested.
 */
class SimilarityEngine(
    private val api: LastfmApi,
    private val dao: MusicDao,
) {
    enum class Why { CROWD, AUDIO, CONSENSUS }

    data class Suggestion(val trackId: Long, val confidence: Int, val why: Why)

    companion object {
        private const val CROWD_WEIGHT = 0.5
        private const val AUDIO_WEIGHT = 0.5
        private const val BOTH_BONUS = 0.1
    }

    suspend fun suggest(seedTrackId: Long, limit: Int = 25): List<Suggestion> {
        val seed = dao.track(seedTrackId) ?: return emptyList()
        val seedFeat = dao.features(seedTrackId)

        // CROWD: resolve Last.fm similars back to owned tracks via the normalized key.
        val crowd = HashMap<Long, Double>()
        api.getSimilar(seed.artist, seed.title, limit = 100).forEachIndexed { rank, (artist, title) ->
            val owned = dao.tracksByNormalizedKey(Normalizer.key(artist, title)).firstOrNull()
            if (owned != null && owned.id != seedTrackId) {
                crowd[owned.id] = (1.0 - rank / 100.0).coerceIn(0.0, 1.0)
            }
        }

        // AUDIO: distance in BPM/key/energy for owned tracks we have features for.
        val audio = HashMap<Long, Double>()
        if (seedFeat != null) {
            // In a full build, page the library; the DAO exposes features per track id.
            crowd.keys.forEach { id ->
                dao.features(id)?.let { f ->
                    val dBpm = abs(seedFeat.bpm - f.bpm) / 40f          // ~40 BPM spread -> 0
                    val dEnergy = abs(seedFeat.energy - f.energy)
                    val keyPenalty = if (seedFeat.camelotKey == f.camelotKey) 0.0 else 0.25
                    audio[id] = (1.0 - (dBpm + dEnergy + keyPenalty)).coerceIn(0.0, 1.0)
                }
            }
        }

        val ids = (crowd.keys + audio.keys).toSet()
        return ids.map { id ->
            val c = crowd[id] ?: 0.0
            val a = audio[id] ?: 0.0
            val both = c > 0.0 && a > 0.0
            val score = (CROWD_WEIGHT * c + AUDIO_WEIGHT * a + if (both) BOTH_BONUS else 0.0)
                .coerceIn(0.0, 1.0)
            val why = when {
                both -> Why.CONSENSUS
                c > 0.0 -> Why.CROWD
                else -> Why.AUDIO
            }
            Suggestion(id, (score * 100).toInt(), why)
        }.sortedByDescending { it.confidence }.take(limit)
    }
}
