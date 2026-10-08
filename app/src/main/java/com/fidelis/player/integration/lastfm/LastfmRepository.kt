package com.fidelis.player.integration.lastfm

import com.fidelis.player.data.secure.SecureTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastfmRepository @Inject constructor(
    private val api: LastfmApiImpl,
    private val scrobbler: LastfmScrobbler,
    private val similarity: SimilarityEngine,
    private val tokenStore: SecureTokenStore,
) {
    private val _connected = MutableStateFlow(tokenStore.lastfmSessionKey != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    suspend fun connect(username: String, password: String): Boolean {
        val sk = api.getMobileSession(username, password) ?: return false
        tokenStore.lastfmSessionKey = sk
        _connected.value = true
        return true
    }

    fun disconnect() { tokenStore.lastfmSessionKey = null; _connected.value = false }

    fun shouldScrobble(positionMs: Long, durationMs: Long) = scrobbler.shouldScrobble(positionMs, durationMs)

    suspend fun updateNowPlaying(artist: String, track: String, album: String?) =
        scrobbler.updateNowPlaying(artist, track, album)

    suspend fun enqueue(artist: String, track: String, album: String?, startedAtSec: Long) =
        scrobbler.enqueue(artist, track, album, startedAtSec)

    suspend fun flush() = scrobbler.flush()

    suspend fun suggest(seedTrackId: Long, limit: Int = 25) = similarity.suggest(seedTrackId, limit)

    suspend fun artistTags(artist: String): List<String> = api.getArtistTags(artist)

    suspend fun trackTags(artist: String, track: String): List<String> = api.getTrackTags(artist, track)
}
