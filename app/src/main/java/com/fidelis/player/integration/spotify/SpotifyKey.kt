package com.fidelis.player.integration.spotify

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.fidelis.player.BuildConfig
import java.security.MessageDigest

/**
 * Which Spotify app Fidelis signs in as.
 *
 * A Spotify app in Development Mode only lets in the accounts its owner lists, so everyone
 * brings their own: they create a Spotify app and paste its Client ID in Profile. Builds made
 * with SPOTIFY_CLIENT_ID in local.properties use that one until another is pasted.
 */
object SpotifyKey {
    private const val PREFS = "fidelis_spotify"
    private const val KEY = "client_id"
    const val REDIRECT_URI = "fidelis://auth"
    const val DASHBOARD = "https://developer.spotify.com/dashboard"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The Client ID pasted in Profile, if any. */
    fun own(context: Context): String? = prefs(context).getString(KEY, null)?.takeIf { it.isNotBlank() }

    /** The one to use: the pasted one, else the one built in. Blank when there's neither. */
    fun clientId(context: Context): String = own(context) ?: BuildConfig.SPOTIFY_CLIENT_ID

    fun isSet(context: Context): Boolean = clientId(context).isNotBlank()

    val builtIn: Boolean get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

    /** Spotify Client IDs are 32 hex characters. */
    fun valid(id: String): Boolean = Regex("[0-9a-fA-F]{32}").matches(id.trim())

    /** Saves a pasted Client ID, or forgets it when null. */
    fun setOwn(context: Context, id: String?) {
        prefs(context).edit().apply {
            if (id.isNullOrBlank()) remove(KEY) else putString(KEY, id.trim().lowercase())
        }.apply()
    }

    /** The SHA-1 of the key this APK is signed with, as Spotify's dashboard wants it (AA:BB:…). */
    fun signingSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
