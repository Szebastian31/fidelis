package com.fidelis.player.annales

import com.fidelis.player.integration.spotify.SpotifyRemote
import kotlinx.coroutines.delay

/**
 * Spotify playback doesn't go through the local player, so the listening log follows the Spotify bridge too: every 10 s
 * while something plays, it adds the time, and counts a track once it has played half its length or 4 min.
 * Runs for as long as the playback service does.
 */
suspend fun ListeningLog.follow(remote: SpotifyRemote) {
    var key: String? = null
    var heardMs = 0L
    var logged = false
    while (true) {
        delay(10_000)
        val s = remote.state.value ?: continue
        if (s.isPaused || s.title.isBlank()) { flush(); continue }
        val k = "${s.artist}\n${s.title}"
        if (k != key) { key = k; heardMs = 0; logged = false }
        listened(10)
        heardMs += 10_000
        if (!logged && (heardMs >= s.durationMs / 2 || heardMs >= 240_000)) {
            logged = true
            played(s.artist, s.title)
        }
    }
}
