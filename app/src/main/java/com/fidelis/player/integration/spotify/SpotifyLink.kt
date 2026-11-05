package com.fidelis.player.integration.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** A pasted Spotify link to a profile or a playlist: open.spotify.com/user/... or spotify:playlist:... */
data class SpotifyLink(val kind: String, val id: String) {
    val isProfile get() = kind == "user"
    val uri get() = "spotify:$kind:$id"

    companion object {
        private val WEB = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(user|playlist)/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
        private val APP = Regex("""spotify:(user|playlist):([^:?#\s]+)""", RegexOption.IGNORE_CASE)

        fun parse(text: String): SpotifyLink? {
            val m = WEB.find(text) ?: APP.find(text) ?: return null
            return SpotifyLink(m.groupValues[1].lowercase(), Uri.decode(m.groupValues[2]))
        }
    }
}

/** Opens a profile or playlist in the Spotify app, or on the web if Spotify isn't installed. */
fun Context.openInSpotify(kind: String, id: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:$kind:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(app)
    } catch (e: ActivityNotFoundException) {
        val web = Uri.parse("https://open.spotify.com/$kind/${Uri.encode(id)}")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
