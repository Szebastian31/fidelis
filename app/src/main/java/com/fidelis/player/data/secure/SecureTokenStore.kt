package com.fidelis.player.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Per-device encrypted storage for the user's own tokens. Nothing here is shipped in the
 * APK or committed to git — these values are produced at runtime when the user signs in.
 */
class SecureTokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "fidelis_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var spotifyAccessToken: String?
        get() = prefs.getString("sp_access", null)
        set(v) = prefs.edit().putString("sp_access", v).apply()

    var spotifyRefreshToken: String?
        get() = prefs.getString("sp_refresh", null)
        set(v) = prefs.edit().putString("sp_refresh", v).apply()

    /** Last.fm per-user session key from auth.getMobileSession. */
    var lastfmSessionKey: String?
        get() = prefs.getString("lf_sk", null)
        set(v) = prefs.edit().putString("lf_sk", v).apply()
}
