package com.fidelis.player.integration.lastfm

import android.os. SystemClock
import com.fidelis.player.data.secure.SecureTokenStore
import com.fidelis.player.integration.spotify.SpotifyRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Scrobbles plays from the Spotify bridge to Last.fm. Call [start] once. */
@Singleton
class BridgeScrobbler @Inject constructor(
    private val remote: SpotifyRemote,
    private val api: LastfmApiImpl,
    private val tokens: SecureTokenStore,
) {
    private data class Pending(val artist: String, val title: String, val startedAtSec: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var started = false

    // The play currently being tracked.
    private var uri: String? = null
    private var title = ""
    private var artist = ""
    private var durationMs = 0L
    private var startedAtSec = 0L
    private var listenedMs = 0L
    private var playingSince: Long? = null // elapsedRealtime when the current playing stretch began
    private var scrobbled = false

    private val pending = ArrayDeque<Pending>()

    fun start() {
        if (started) return
        started = true
        scope.launch { remote.state.collect { s -> lock.withLock { onState(s) } } }
        // Player events only fire on changes, so check progress regularly while playing.
        scope.launch {
            while (isActive) {
                delay(15_000)
                lock.withLock { tick(); checkThreshold() }
            }
        }
    }

    private suspend fun onState(s: SpotifyRemote.Snapshot?) {
        if (s == null) return
        val now = SystemClock.elapsedRealtime()
        val replay = s.uri == uri && scrobbled && s.positionMs < 3_000 && !s.isPaused
        if (s.uri != uri || replay) {
            tick(); checkThreshold() // finish the previous play first
            uri = s.uri; title = s.title; artist = s.artist; durationMs = s.durationMs
            startedAtSec = System.currentTimeMillis() / 1000 - s.positionMs / 1000
            listenedMs = 0; scrobbled = false
            playingSince = if (s.isPaused) null else now
            if (!s.isPaused) nowPlaying()
            return
        }
        tick() // same song: pause, resume or seek
        playingSince = if (s.isPaused) null else now
        if (!s.isPaused && durationMs == 0L) durationMs = s.durationMs
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        playingSince?.let { listenedMs += now - it; playingSince = now }
    }

    private fun isTrack() =
        uri?.startsWith("spotify:track:") == true && artist.isNotBlank() && title.isNotBlank()

    private suspend fun nowPlaying() {
        val sk = tokens.lastfmSessionKey ?: return
        if (isTrack()) api.updateNowPlaying(sk, artist, title, durationMs / 1000)
    }

    private suspend fun checkThreshold() {
        if (scrobbled || !isTrack() || durationMs < 30_000) return
        if (listenedMs < minOf(durationMs / 2, 240_000L)) return
        scrobbled = true
        if (tokens.lastfmSessionKey == null) return
        pending.addLast(Pending(artist, title, startedAtSec))
        while (pending.size > 50) pending.removeFirst()
        flush()
    }

    private suspend fun flush() {
        val sk = tokens.lastfmSessionKey ?: return
        while (pending.isNotEmpty()) {
            val p = pending.first()
            if (api.scrobble(sk, p.artist, p.title, p.startedAtSec)) pending.removeFirst() else break
        }
    }
}
