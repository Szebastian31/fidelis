package com.fidelis.player.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An audio session another app announced, so effects can be attached to it. */
data class AnnouncedSession(val packageName: String, val session: Int, val at: Long)

/** Tracks the audio session Spotify announces. */
object SoundSession {
    private val _spotify = MutableStateFlow<AnnouncedSession?>(null)
    val spotify: StateFlow<AnnouncedSession?> = _spotify.asStateFlow()

    internal fun opened(pkg: String, session: Int) {
        if (pkg == SPOTIFY) _spotify.value = AnnouncedSession(pkg, session, System.currentTimeMillis())
    }

    internal fun closed(pkg: String, session: Int) {
        if (pkg == SPOTIFY && _spotify.value?.session == session) _spotify.value = null
    }

    const val SPOTIFY = "com.spotify.music"
}

/** Receives "audio session opened / closed" announcements from music apps. */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val pkg = intent.getStringExtra(AudioEffect. EXTRA_PACKAGE_NAME).orEmpty()
        if (session <= 0 || pkg.isBlank()) return
        SoundEngine.init(context)
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> SoundSession.opened(pkg, session)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> SoundSession.closed(pkg, session)
        }
    }
}
