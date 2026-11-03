package com.fidelis.player.ui.playlists

import android.content.Context
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.ListItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Something pinned to Your playlists: one of yours, or any playlist or mix from Spotify or other people. */
data class Pin(
    val uri: String,
    val title: String = "",
    val subtitle: String = "",
    /** Spotify's image id ("spotify:image:…") or a web address. */
    val image: String? = null,
    val id: String = "",
    val hasChildren: Boolean = true,
) {
    val playlistId: String get() = uri.substringAfter("spotify:playlist:", "")
    fun toItem() = ListItem(id.ifBlank { uri }, uri, image?.let { ImageUri(it) }, title, subtitle, true, hasChildren)
}

/** One tile in Your playlists. */
sealed interface Shelf {
    val key: String
    data class Mine(val p: SpotifyPlaylist) : Shelf { override val key: String get() = p.id }
    data class Other(val pin: Pin) : Shelf { override val key: String get() = pin.uri }
}

/** Pinned to the front of Your playlists, newest pin first. Kept on the phone. */
@Singleton
class PlaylistPins @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fidelis_pins", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Pin>> = _items.asStateFlow()
    private val _ids = MutableStateFlow(idsOf(_items.value))
    /** Pinned playlist ids, for the tiles of your own playlists. */
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    /** One of your playlists, by id. */
    fun toggle(id: String) = toggle(Pin("spotify:playlist:$id"))

    /** Anything Spotify shows: its mixes, editorial playlists, other people's playlists. */
    fun toggle(item: ListItem) = toggle(Pin(item.uri.orEmpty(), item.title.orEmpty(), item.subtitle.orEmpty(),
        item.imageUri?.raw, item.id.orEmpty(), item.hasChildren))

    private fun toggle(pin: Pin) {
        if (pin.uri.isBlank()) return
        val list = _items.value
        save(if (list.any { it.uri == pin.uri }) list.filterNot { it.uri == pin.uri } else listOf(pin) + list)
    }

    private fun save(now: List<Pin>) {
        _items.value = now
        _ids.value = idsOf(now)
        val arr = JSONArray()
        now.forEach { p ->
            arr.put(JSONObject().put("uri", p.uri).put("title", p.title).put("subtitle", p.subtitle)
                .put("image", p.image ?: "").put("id", p.id).put("children", p.hasChildren))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun load(): List<Pin> {
        prefs.getString("items", null)?.let { s ->
            return runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Pin(o.getString("uri"), o.optString("title"), o.optString("subtitle"),
                        o.optString("image").ifBlank { null }, o.optString("id"), o.optBoolean("children", true))
                }
            }.getOrDefault(emptyList())
        }
        // Earlier versions kept only your own playlists' ids.
        return prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() }.map { Pin("spotify:playlist:$it") }
    }

    private fun idsOf(list: List<Pin>) = list.map { it.playlistId }.filter { it.isNotBlank() }
}
