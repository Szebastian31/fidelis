package com.fidelis.player.mood

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class Mix(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<MoodTrack>,
    val createdAt: Long,
) {
    val covers: List<String> get() = tracks.mapNotNull { it.imageUrl }.distinct().take(4)
    val newCount: Int get() = tracks.count { it.isNew }
}

/** Your generated mixes, saved to disk so the tiles survive a restart. */
@Singleton
class MixStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "mixes.json")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mixes = MutableStateFlow(load())
    val mixes: StateFlow<List<Mix>> = _mixes.asStateFlow()

    fun get(id: String): Mix? = _mixes.value.firstOrNull { it.id == id }

    /** Adds a new mix at the front; a mix with the same title is replaced. */
    fun add(title: String, subtitle: String, tracks: List<MoodTrack>): Mix {
        val mix = Mix(System.currentTimeMillis().toString(36), title, subtitle, tracks, System.currentTimeMillis())
        _mixes.value = (listOf(mix) + _mixes.value.filterNot { it.title == title }).take(MAX)
        save()
        return mix
    }

    /** Replaces a mix's songs in place (e.g. regenerate), keeping its id. */
    fun update(id: String, tracks: List<MoodTrack>) {
        _mixes.value = _mixes.value.map { if (it.id == id) it.copy(tracks = tracks, createdAt = System.currentTimeMillis()) else it }
        save()
    }

    fun delete(id: String) { _mixes.value = _mixes.value.filterNot { it.id == id }; save() }

    private fun save() {
        val snapshot = _mixes.value
        io.launch {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { m ->
                    arr.put(JSONObject()
                        .put("id", m.id).put("title", m.title).put("subtitle", m.subtitle).put("at", m.createdAt)
                        .put("tracks", JSONArray().apply { m.tracks.forEach { put(it.toJson()) } }))
                }
                val tmp = File(file.path + ".tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): List<Mix> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val ts = o.getJSONArray("tracks")
            Mix(o.getString("id"), o.getString("title"), o.optString("subtitle"),
                List(ts.length()) { ts.getJSONObject(it).toTrack() }, o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun MoodTrack.toJson() = JSONObject()
        .put("u", uri).put("t", title).put("a", artist).put("s", score)
        .put("m", JSONArray(matched)).put("i", imageUrl ?: "").put("n", isNew)

    private fun JSONObject.toTrack(): MoodTrack {
        val m = optJSONArray("m") ?: JSONArray()
        return MoodTrack(getString("u"), getString("t"), getString("a"), optInt("s"),
            List(m.length()) { m.getString(it) }, optString("i").ifBlank { null }, optBoolean("n"))
    }

    private companion object { const val MAX = 24 }
}
