package com.fidelis.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os. Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.fidelis.player.MainActivity
import com.fidelis.player.R
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.ui.theme.Fidelis
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Publishes a MediaStyle notification + MediaSession that mirror the Spotify bridge
 * (SpotifyRemote). This is what feeds ColorOS's lock-screen media card and the
 * Fluid Cloud capsule on the Find X9 Pro. Transport controls route back to the bridge.
 */
@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var remote: SpotifyRemote

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "Fidelis").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = remote.resume()
                override fun onPause() = remote.pause()
                override fun onSkipToNext() = remote.skipNext()
                override fun onSkipToPrevious() = remote.skipPrevious()
                override fun onSeekTo(pos: Long) = remote.seekTo(pos)
                override fun onStop() { remote.pause(); stopSelf() }
            })
            isActive = true
        }
        // keep the session + notification in lockstep with the bridge
        scope.launch {
            combine(remote.state, remote.artwork) { snap, art -> snap to art }
                .collect { (snap, art) ->
                    updateSession(snap, art)
                    startForegroundCompat(buildNotification(snap, art))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (remote.state.value?.isPaused != false) remote.resume() else remote.pause()
            ACTION_NEXT -> remote.skipNext()
            ACTION_PREV -> remote.skipPrevious()
            ACTION_STOP -> { remote.pause(); stopSelf(); return START_NOT_STICKY }
        }
        // Promote to foreground immediately so we never trip the 5s FGS deadline.
        startForegroundCompat(buildNotification(remote.state.value, remote.artwork.value))
        return START_STICKY
    }

    private fun updateSession(snap: SpotifyRemote.Snapshot?, art: Bitmap?) {
        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snap?.title ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snap?.artist ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snap?.durationMs ?: 0L)
        if (art != null) meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
        session.setMetadata(meta.build())

        val paused = snap?.isPaused ?: true
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat. ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
                    snap?.positionMs ?: 0L,
                    if (paused) 0f else 1f,
                )
                .build()
        )
    }

    private fun buildNotification(snap: SpotifyRemote.Snapshot?, art: Bitmap?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fidelis)
            .setContentTitle(snap?.title?.ifBlank { null } ?: "Fidelis")
            .setContentText(snap?.artist ?: "")
            .setLargeIcon(art)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setDeleteIntent(action(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(snap?.isPaused == false)
            .setColorized(true)
            .setColor(Fidelis.purple.toArgb()) // follows the selected theme
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREV))
            .addAction(
                if (snap?.isPaused == false) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (snap?.isPaused == false) "Pause" else "Play", action(ACTION_TOGGLE),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this, name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "fidelis_playback"
        private const val NOTIF_ID = 1001
        const val ACTION_TOGGLE = "com.fidelis.player.action.TOGGLE"
        const val ACTION_NEXT = "com.fidelis.player.action.NEXT"
        const val ACTION_PREV = "com.fidelis.player.action.PREV"
        const val ACTION_STOP = "com.fidelis.player.action.STOP"
    }
}
