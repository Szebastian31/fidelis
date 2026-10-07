package com.fidelis.player.integration.spotify

import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * Spotify OAuth 2.0 with PKCE — the correct flow for a mobile app with NO client secret.
 * Each user authorizes their own account; the app ships only the public client id.
 *
 * Flow:
 *   1. buildAuthUrl() -> open in a Custom Tab
 *   2. user approves -> Spotify redirects to fidelis://auth?code=...
 *   3. exchange the code + the SAME code_verifier for tokens (see SpotifyAuthApi)
 *   4. store tokens per-device in EncryptedSharedPreferences (see SecureTokenStore)
 *
 * Read-only scopes only — we never request playback or streaming scopes, because audio
 * never comes from Spotify.
 */
class SpotifyPkce(private val clientId: String, private val redirectUri: String) {

    val codeVerifier: String = randomUrlSafe(64)

    private val scopes = listOf(
        "user-library-read",
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-top-read",
    )

    fun buildAuthUrl(state: String): String {
        val challenge = s256(codeVerifier)
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", scopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .build().toString()
    }

    private fun s256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

/**
 * Spotify library import seam. Back these with Retrofit against api.spotify.com.
 * NOTE: do NOT call recommendations / audio-features / related-artists — those were
 * removed for new apps in Nov 2024. Similarity is computed locally.
 */
interface SpotifyLibraryApi {
    suspend fun likedTracks(): List<SpotifyTrack>
    suspend fun playlistTracks(playlistId: String): List<SpotifyTrack>
    suspend fun savedAlbumsTracks(): List<SpotifyTrack>
    suspend fun topTracks(): List<SpotifyTrack>
}
