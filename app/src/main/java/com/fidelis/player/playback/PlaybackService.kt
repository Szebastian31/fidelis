package com.fidelis.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics. Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.fidelis.player.MainActivity
import com.fidelis.player.annales.ListeningLog
import com.fidelis.player.annales.follow
import com.fidelis.player.R
import com.fidelis.player.integration.spotify.QueueEntry
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.widget.HomeWidget
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
 * (SpotifyRemote). This feeds ColorOS's lock-screen media card and the Fluid Cloud
 * capsule on the Find X9 Pro. Adds ❤ and Shuffle buttons, and shares Up next.
 */
@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var remote: SpotifyRemote

    private data class Frame(
        val snap: SpotifyRemote.Snapshot?, val art: Bitmap?, val liked: Boolean, val queue: List<QueueEntry>,
    ) {
        val hasSong get() = snap != null && snap.title.isNotBlank()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private var sharedQueue: List<QueueEntry>? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch { ListeningLog.get(this@PlaybackService).follow(remote) } // what you listen to, for Annales
        session = MediaSessionCompat(this, "Fidelis").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = remote.resume()
                override fun onPause() = remote.pause()
                override fun onSkipToNext() = remote.skipNext()
                override fun onSkipToPrevious() = remote.skipPrevious()
                override fun onSeekTo(pos: Long) = remote.seekTo(pos)
                override fun onStop() { remote.pause(); stopSelf() }
                override fun onSkipToQueueItem(id: Long) {
                    val i = remote.queue.value.indexOfFirst { it.id == id }
                    if (i >= 0) remote.playAt(i)
                }
                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        CUSTOM_LIKE -> remote.toggleLike()
                        CUSTOM_SHUFFLE -> remote.reshuffle()
                    }
                }
            })
            setSessionActivity(openApp())
            setQueueTitle("Up next")
        }
        // Keep the session + notification in step with the bridge.
        scope.launch {
            combine(remote.state, remote.artwork, remote.liked, remote.queue) { s, a, l, q -> Frame(s, a, l, q) }
                .collect { f ->
                    updateSession(f)
                    HomeWidget.spotify(this@PlaybackService, f.snap?.title, f.snap?.artist, f.art, f.snap?.isPaused == false)
                    startForegroundCompat(buildNotification(f))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (remote.state.value?.isPaused != false) remote.resume() else remote.pause()
            ACTION_NEXT -> remote.skipNext()
            ACTION_PREV -> remote.skipPrevious()
            ACTION_LIKE -> remote.toggleLike()
            ACTION_STOP -> { remote.pause(); stopSelf(); return START_NOT_STICKY }
        }
        // Promote to foreground immediately so we never trip the 5s FGS deadline.
        startForegroundCompat(buildNotification(currentFrame()))
        return START_STICKY
    }

    private fun currentFrame() =
        Frame(remote.state.value, remote.artwork.value, remote.liked.value, remote.queue.value)

    private fun updateSession(f: Frame) {
        // An active session with no song shows up as a blank card in ColorOS. Stay quiet until there's one.
        session.isActive = f.hasSong
        if (!f.hasSong) return
        val snap = f.snap!!

        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, snap.uri ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snap.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snap.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snap.durationMs)
        if (f.art != null) meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, f.art)
        session.setMetadata(meta.build())

        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (snap.isPaused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
                snap.positionMs,
                if (snap.isPaused) 0f else 1f,
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_LIKE,
                    if (f.liked) "Remove from Liked Songs" else "Save to Liked Songs",
                    if (f.liked) R.drawable.ic_action_liked else R.drawable.ic_action_like,
                ).build()
            )
        if (f.queue.size > 1) {
            state.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(CUSTOM_SHUFFLE, "Shuffle Up next", R.drawable.ic_action_shuffle).build()
            )
        }
        session.setPlaybackState(state.build())

        // Share Up next only when it changes; it can be long.
        if (f.queue !== sharedQueue) {
            sharedQueue = f.queue
            session.setQueue(f.queue.take(MAX_SHARED).map { e ->
                MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(e.track.uri).setTitle(e.track.title).setSubtitle(e.track.artist).build(),
                    e.id,
                )
            })
        }
    }

    private fun buildNotification(f: Frame): Notification {
        val base = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fidelis)
            .setContentIntent(openApp())
            .setDeleteIntent(action(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setColor(Fidelis.purple.toArgb()) // follows the selected look

        // Nothing loaded yet: a plain, quiet notice instead of an empty media card.
        if (!f.hasSong) {
            return base.setContentTitle("Fidelis").setContentText("Waiting for Spotify")
                .setPriority(NotificationCompat.PRIORITY_MIN).build()
        }

        val snap = f.snap!!
        val playing = !snap.isPaused
        return base
            .setContentTitle(snap.title)
            .setContentText(snap.artist)
            .setLargeIcon(f.art)
            .setOngoing(playing)
            .setColorized(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREV))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play", action(ACTION_TOGGLE),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))
            .addAction(
                if (f.liked) R.drawable.ic_action_liked else R.drawable.ic_action_like,
                if (f.liked) "Remove from Liked Songs" else "Save to Liked Songs", action(ACTION_LIKE),
            )
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this, name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startForegroundCompat(notification: Notification) {
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
        private const val MAX_SHARED = 100
        private const val CUSTOM_LIKE = "com.fidelis.player.custom.LIKE"
        private const val CUSTOM_SHUFFLE = "com.fidelis.player.custom.SHUFFLE"
        const val ACTION_TOGGLE = "com.fidelis.player.action.TOGGLE"
        const val ACTION_NEXT = "com.fidelis.player.action.NEXT"
        const val ACTION_PREV = "com.fidelis.player.action.PREV"
        const val ACTION_LIKE = "com.fidelis.player.action.LIKE"
        const val ACTION_STOP = "com.fidelis.player.action.STOP"
    }
}
