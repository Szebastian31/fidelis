package com.fidelis.player.integration.lastfm

import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.data.db.ScrobbleEntity
import java.security.MessageDigest

/**
 * Last.fm scrobbling (Scrobbling 2.0).
 *
 * Rules enforced here:
 *   - a track must be longer than 30s to be scrobbled at all
 *   - scrobble once it has played for half its length OR 4 minutes, whichever comes first
 *   - "now playing" is sent on start (not queued)
 *   - scrobbles are queued in Room and flushed in batches of up to 50, so offline plays
 *     are not lost
 *
 * Each user signs in with their own account: [sessionKey] comes from auth.getMobileSession
 * and is stored per-device in EncryptedSharedPreferences. The app only ships the public
 * api key + shared secret used to sign requests.
 */
class LastfmScrobbler(
    private val api: LastfmApi,
    private val dao: MusicDao,
    private val apiKey: String,
    private val secret: String,
    private val sessionKeyProvider: () -> String?,
) {
    companion object {
        private const val MIN_TRACK_MS = 30_000L
        private const val SCROBBLE_AFTER_MS = 240_000L // 4 minutes
    }

    fun shouldScrobble(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs < MIN_TRACK_MS) return false
        return positionMs >= durationMs / 2 || positionMs >= SCROBBLE_AFTER_MS
    }

    suspend fun updateNowPlaying(artist: String, track: String, album: String?) {
        val sk = sessionKeyProvider() ?: return
        val params = sortedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to artist, "track" to track,
            "api_key" to apiKey, "sk" to sk,
        )
        album?.let { params["album"] = it }
        api.post(sign(params))
    }

    /** Called when [shouldScrobble] first turns true for the current track. */
    suspend fun enqueue(artist: String, track: String, album: String?, startedAtEpochSec: Long) {
        dao.enqueueScrobble(ScrobbleEntity(artist = artist, track = track, album = album, startedAtEpochSec = startedAtEpochSec))
        flush()
    }

    /** Flush pending scrobbles in one batch of up to 50; mark sent on success. */
    suspend fun flush() {
        val sk = sessionKeyProvider() ?: return
        val pending = dao.pendingScrobbles()
        if (pending.isEmpty()) return

        val params = sortedMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey, "sk" to sk,
        )
        pending.forEachIndexed { i, s ->
            params["artist[$i]"] = s.artist
            params["track[$i]"] = s.track
            params["timestamp[$i]"] = s.startedAtEpochSec.toString()
            s.album?.let { params["album[$i]"] = it }
        }
        val ok = api.post(sign(params))
        if (ok) dao.markScrobbled(pending.map { it.id })
    }

    /** Append the md5 api_sig over all params (sorted), then return the signed map. */
    private fun sign(params: MutableMap<String, String>): Map<String, String> {
        val sig = buildString {
            params.toSortedMap().forEach { (k, v) -> append(k); append(v) }
            append(secret)
        }
        params["api_sig"] = md5(sig)
        params["format"] = "json"
        return params
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** Thin transport seam so the scrobbler is testable; back it with Retrofit/OkHttp. */
interface LastfmApi {
    /** POST to https://ws.audioscrobbler.com/2.0/ ; returns true on 2xx without a Last.fm error. */
    suspend fun post(params: Map<String, String>): Boolean

    /** track.getSimilar -> list of (artist, title) ranked by crowd match. */
    suspend fun getSimilar(artist: String, track: String, limit: Int = 50): List<Pair<String, String>>
}
