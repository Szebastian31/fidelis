package com.fidelis.player.annales

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * A small per-day listening log, kept on the phone for Annales: seconds of actual playback and how many times each track
 * was properly heard (half its length or 4 min, the scrobble rule). Independent of Last.fm, and outside Room so the
 * music database never needs a migration. Keeps the last 120 days.
 */
class ListeningLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("annales_share", Context.MODE_PRIVATE)
    private var pendingSec = 0

    data class Day(val seconds: Int, val plays: Map<Pair<String, String>, Int>)

    /** Called every few seconds while music is actually playing. Written out once a minute. */
    @Synchronized fun listened(seconds: Int) {
        pendingSec += seconds
        if (pendingSec >= 60) flush()
    }

    @Synchronized fun played(artist: String, title: String) {
        update { o ->
            val plays = o.optJSONObject("plays") ?: JSONObject()
            val key = "$artist\n$title"
            plays.put(key, plays.optInt(key) + 1)
            o.put("plays", plays)
        }
    }

    @Synchronized fun flush() {
        if (pendingSec == 0) return
        val add = pendingSec
        pendingSec = 0
        update { o -> o.put("sec", o.optInt("sec") + add) }
    }

    fun day(date: LocalDate): Day? {
        val o = prefs.getString("d:$date", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val extra = if (date == LocalDate.now()) pendingSec else 0
        val plays = buildMap {
            o.optJSONObject("plays")?.let { p ->
                p.keys().forEach { k ->
                    val (artist, title) = k.split("\n", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    put(artist to title, p.optInt(k))
                }
            }
        }
        return Day(o.optInt("sec") + extra, plays)
    }

    private fun update(change: (JSONObject) -> Unit) {
        val today = LocalDate.now()
        val key = "d:$today"
        val o = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        change(o)
        val edit = prefs.edit().putString(key, o.toString())
        val oldest = today.minusDays(120)
        prefs.all.keys.filter { it.startsWith("d:") && runCatching { LocalDate.parse(it.drop(2)) < oldest }.getOrDefault(true) }
            .forEach { edit.remove(it) }
        edit.apply()
    }

    companion object {
        @Volatile private var instance: ListeningLog? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: ListeningLog(context).also { instance = it } }
    }
}
