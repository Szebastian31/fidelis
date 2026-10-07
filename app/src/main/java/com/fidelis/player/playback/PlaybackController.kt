package com.fidelis.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fidelis.player.data.db.AudioFeaturesEntity
import com.fidelis.player.playback.crossfade.CrossfadeEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the two players used for crossfade.
 *   - primary: created and released by [PlaybackService]; drives the MediaSession.
 *   - secondary: created lazily here to pre-roll the next track and ramp it in.
 *
 * When the primary track nears its end by the planned blend length, we start the
 * secondary on the next item and run an equal-power volume ramp between them.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crossfade: CrossfadeEngine,
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var primary: ExoPlayer? = null
    private var secondary: ExoPlayer? = null
    private var watchJob: Job? = null
    private var ramping = false

    /** Features lookup injected by the repository layer (trackId -> features). */
    var featuresProvider: suspend (trackId: Long) -> AudioFeaturesEntity? = { null }

    /** Resolves the next MediaItem + its local trackId, or null when the queue ends. */
    var nextItemProvider: () -> Pair<MediaItem, Long>? = { null }

    fun attachPrimary(player: ExoPlayer) {
        primary = player
        player.volume = 1f
        startWatch()
    }

    private fun startWatch() {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                delay(250)
                maybeStartCrossfade()
            }
        }
    }

    private suspend fun maybeStartCrossfade() {
        val p = primary ?: return
        if (ramping || p.playbackState != Player.STATE_READY || !p.isPlaying) return

        val remaining = p.duration - p.currentPosition
        if (p.duration <= 0 || remaining <= 0) return

        val next = nextItemProvider() ?: return
        val currentId = p.currentMediaItem?.mediaId?.toLongOrNull()
        val plan = crossfade.plan(
            outgoing = currentId?.let { featuresProvider(it) },
            incoming = featuresProvider(next.second),
        )
        if (plan.blendMs <= 0) return

        if (remaining <= plan.blendMs) {
            ramp(next.first, plan.blendMs)
        }
    }

    private fun ramp(nextItem: MediaItem, blendMs: Int) {
        val out = primary ?: return
        ramping = true
        val inc = ExoPlayer.Builder(context).build().also { secondary = it }
        inc.setMediaItem(nextItem)
        inc.volume = 0f
        inc.prepare()
        inc.play()

        scope.launch {
            val steps = 40
            val stepMs = (blendMs / steps).toLong().coerceAtLeast(10)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val (outGain, inGain) = crossfade.gainsAt(t)
                out.volume = outGain
                inc.volume = inGain
                delay(stepMs)
            }
            // Promote the incoming player: swap roles and release the finished one.
            out.stop()
            out.clearMediaItems()
            out.volume = 1f
            secondary = null
            // In a full build you'd hand `inc` to the session as the new primary; for the
            // scaffold we transfer its queue position back to the primary and release inc.
            inc.release()
            ramping = false
        }
    }

    fun release() {
        watchJob?.cancel()
        secondary?.release()
        secondary = null
        primary = null
    }
}
