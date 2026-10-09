package com.fidelis.player.mood

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Last.fm tags persisted to disk, so mixes are instant after the first build. */
@Singleton
class TagCache @Inject constructor(@ApplicationContext context: Context) {
    private data class Entry(val tags: List<String>, val at: Long)

    private val file = File(context.filesDir, "lastfm_tags.json")
    private val map = ConcurrentHashMap<String, Entry>()
    private val lock = Mutex()
    @Volatile private var loaded = false
    @Volatile private var dirty = false

    private suspend fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return@withLock
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!file.exists()) return@runCatching
                    val root = JSONObject(file.readText())
                    for (k in root.keys()) {
                        val o = root.getJSONObject(k)
                        val arr = o.getJSONArray("g")
                        map[k] = Entry(List(arr.length()) { arr.getString(it) }, o.getLong("t"))
                    }
                }
            }
            loaded = true
        }
    }

    suspend fun get(key: String): List<String>? {
        ensureLoaded()
        val e = map[key] ?: return null
        val age = System.currentTimeMillis() - e.at
        return if (age > TTL || (e.tags.isEmpty() && age > EMPTY_TTL)) null else e.tags
    }

    suspend fun put(key: String, tags: List<String>) {
        ensureLoaded()
        map[key] = Entry(tags, System.currentTimeMillis())
        dirty = true
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        if (!dirty) return@withContext
        dirty = false
        if (map.size > MAX_ENTRIES) {
            map.entries.sortedBy { it.value.at }.take(map.size - MAX_ENTRIES).forEach { map.remove(it.key) }
        }
        val root = JSONObject()
        map.forEach { (k, e) -> root.put(k, JSONObject().put("t", e.at).put("g", JSONArray(e.tags))) }
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val TTL = 30L * 24 * 60 * 60 * 1000
        const val EMPTY_TTL = 24L * 60 * 60 * 1000
        const val MAX_ENTRIES = 6000
    }
}
