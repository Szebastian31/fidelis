package com.fidelis.player.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

class SecureTokenStore(private val context: Context) {

    // If the encrypted store can't be opened (e.g. the Keystore master key was
    // regenerated after a reinstall → AEADBadTagException), wipe the stale file +
    // key and recreate, instead of crashing the whole app at startup.
    private val prefs: SharedPreferences = runCatching { create() }
        .getOrElse {
            reset()
            create()
        }

    private fun create(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey. Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun reset() {
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        runCatching {
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

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

    companion object {
        private const val PREFS_NAME = "fidelis_secure"
    }
}
