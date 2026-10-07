package com.fidelis.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.fidelis.player.integration.lastfm.LastfmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the player to Last.fm. On each new track it sends "now playing"; once the
 * track crosses the scrobble threshold (half its length or 4 min) it's queued exactly
 * once. Queueing goes through Room, so offline plays flush automatically later.
 */
@Singleton
class ScrobbleManager @Inject constructor(
    private val lastfm: LastfmRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var pollJob: Job? = null
    private var currentKey: String? = null
    private var startedAtSec: Long = 0
    private var scrobbled = false

    fun attach(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = onTrackStart(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && currentKey == null) onTrackStart(player)
            }
        })
        startPolling(player)
    }

    private fun onTrackStart(player: Player) {
        val md = player.currentMediaItem?.mediaMetadata ?: return
        val artist = md.artist?.toString().orEmpty()
        val title = md.title?.toString().orEmpty()
        if (artist.isBlank() || title.isBlank()) return
        currentKey = "$artist|$title"
        startedAtSec = System.currentTimeMillis() / 1000
        scrobbled = false
        val album = md.albumTitle?.toString()
        scope.launch { lastfm.updateNowPlaying(artist, title, album) }
    }

    private fun startPolling(player: Player) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (scrobbled || currentKey == null) continue
                val duration = player.duration
                if (duration <= 0) continue
                if (lastfm.shouldScrobble(player.currentPosition, duration)) {
                    val md = player.currentMediaItem?.mediaMetadata
                    val artist = md?.artist?.toString().orEmpty()
                    val title = md?.title?.toString().orEmpty()
                    if (artist.isNotBlank() && title.isNotBlank()) {
                        scrobbled = true
                        lastfm.enqueue(artist, title, md?.albumTitle?.toString(), startedAtSec)
                    }
                }
            }
        }
    }
}
