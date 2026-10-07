package com.fidelis.player.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    var spotifyCodeVerifier: String?
        get() = prefs.getString("sp_verifier", null)
        set(v) = prefs.edit().putString("sp_verifier", v).apply()

    var lastfmSessionKey: String?
        get() = prefs.getString("lf_sk", null)
        set(v) = prefs.edit().putString("lf_sk", v).apply()
}
