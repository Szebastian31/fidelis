package com.fidelis.player.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An audio session another app announced, so effects can be attached to it. */
data class AnnouncedSession(val packageName: String, val session: Int, val at: Long)

/** Tracks the audio session Spotify announces, and runs a short audible test on it. */
object SoundSession {
    private val _spotify = MutableStateFlow<AnnouncedSession?>(null)
    val spotify: StateFlow<AnnouncedSession?> = _spotify.asStateFlow()
    private val _lastSeen = MutableStateFlow<AnnouncedSession?>(null)
    /** The last session from any app, to tell "nothing announced" from "only others announce". */
    val lastSeen: StateFlow<AnnouncedSession?> = _lastSeen.asStateFlow()

    internal fun opened(pkg: String, session: Int) {
        val s = AnnouncedSession(pkg, session, System.currentTimeMillis())
        _lastSeen.value = s
        if (pkg == SPOTIFY) _spotify.value = s
    }

    internal fun closed(pkg: String, session: Int) {
        if (pkg == SPOTIFY && _spotify.value?.session == session) _spotify.value = null
    }

    /**
     * Cuts everything above ~800 Hz for 5 seconds on Spotify's session (or the whole output
     * if Spotify hasn't announced one). Returns what happened, in plain words.
     */
    fun test(): String {
        val target = _spotify.value?.session ?: 0
        return try {
            val eq = Equalizer(1000, target)
            val min = eq.bandLevelRange[0]
            for (b in 0 until eq.numberOfBands) {
                if (eq.getCenterFreq(b.toShort()) > 800_000) eq.setBandLevel(b.toShort(), min)
            }
            eq.enabled = true
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { eq.release() } }, 5_000)
            if (target == 0) "Applied to the whole phone output (Spotify hasn't announced its session)."
            else "Applied to Spotify's session #$target."
        } catch (t: Throwable) {
            "Android refused the effect: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    const val SPOTIFY = "com.spotify.music"
}

/** Receives "audio session opened / closed" announcements from music apps. */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty()
        if (session <= 0 || pkg.isBlank()) return
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> SoundSession.opened(pkg, session)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> SoundSession.closed(pkg, session)
        }
    }
}
