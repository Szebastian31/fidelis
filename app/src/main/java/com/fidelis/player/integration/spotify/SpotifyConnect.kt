package com.fidelis.player.integration.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectDevice(
    val id: String, val name: String, val type: String,
    val active: Boolean, val restricted: Boolean,
)

sealed interface DeviceList {
    object Loading : DeviceList
    data class Ready(val devices: List<ConnectDevice>) : DeviceList
    /** Signed in before Fidelis asked for device access: sign in again once. */
    object NeedsReconnect : DeviceList
    object SignedOut : DeviceList
    object Failed : DeviceList
}

/** Spotify Connect: the speakers, TVs and computers Spotify can play on, and moving playback between them. */
@Singleton
class SpotifyConnect @Inject constructor(private val repo: SpotifyRepository) {

    suspend fun devices(): DeviceList = withContext(Dispatchers.IO) {
        if (!repo.connected.value) return@withContext DeviceList.SignedOut
        val (code, body) = repo.send("GET", "$API/me/player/devices")
        when (code) {
            200 -> runCatching {
                val arr = JSONObject(body).optJSONArray("devices") ?: JSONArray()
                DeviceList.Ready((0 until arr.length()).mapNotNull { i ->
                    val d = arr.getJSONObject(i)
                    val id = d.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
                    ConnectDevice(id, d.optString("name"), d.optString("type"), d.optBoolean("is_active"), d.optBoolean("is_restricted"))
                }.sortedWith(compareByDescending<ConnectDevice> { it.active }.thenBy { it.name.lowercase() }))
            }.getOrDefault(DeviceList.Failed)
            401 -> if (repo.connected.value) DeviceList.NeedsReconnect else DeviceList.SignedOut
            403 -> DeviceList.NeedsReconnect
            else -> DeviceList.Failed
        }
    }

    /** Moves playback to a device and keeps it playing. */
    suspend fun transfer(id: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("device_ids", JSONArray().put(id)).put("play", true).toString()
        repo.send("PUT", "$API/me/player", body).first in 200..299
    }

    fun reconnectUrl(): String = repo.authUrl()

    private companion object { const val API = "https://api.spotify.com/v1" }
}
