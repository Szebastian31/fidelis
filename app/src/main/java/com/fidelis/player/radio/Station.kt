package com.fidelis.player.radio

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One live station from the Radio Browser directory. */
data class Station(
    val id: String, val name: String, val stream: String, val favicon: String,
    val tags: List<String>, val country: String, val countryCode: String,
    val codec: String, val bitrate: Int, val hls: Boolean,
) {
    /** "Poland · Pop · 128 kbps" */
    val meta: String get() = listOfNotNull(
        country.ifBlank { null },
        tags.firstOrNull()?.replaceFirstChar { it.titlecase() },
        bitrate.takeIf { it > 0 }?.let { "$it kbps" },
    ).joinToString(" · ")

    fun toJson(): JSONObject = JSONObject().put("stationuuid", id).put("name", name).put("url_resolved", stream)
        .put("favicon", favicon).put("tags", tags.joinToString(",")).put("country", country)
        .put("countrycode", countryCode).put("codec", codec).put("bitrate", bitrate).put("hls", if (hls) 1 else 0)

    companion object {
        fun from(o: JSONObject): Station? {
            val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name").trim()
            if (stream.isBlank() || name.isBlank()) return null
            return Station(
                o.optString("stationuuid"), name, stream, o.optString("favicon"),
                o.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }.take(4),
                o.optString("country"), o.optString("countrycode"), o.optString("codec"),
                o.optInt("bitrate"), o.optInt("hls") == 1,
            )
        }
    }
}

data class RadioCountry(val code: String, val name: String, val stations: Int) {
    val flag: String get() = flagOf(code)
}

fun flagOf(code: String): String =
    if (code.length != 2) "🌍" else code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/** Where you are, from the phone network, then the SIM, then the language settings. */
fun homeCountry(context: Context): String {
    val tm = context.getSystemService(TelephonyManager::class.java)
    val net = runCatching { tm?.networkCountryIso }.getOrNull().orEmpty()
    val sim = runCatching { tm?.simCountryIso }.getOrNull().orEmpty()
    return listOf(net, sim, Locale.getDefault().country).firstOrNull { it.length == 2 }?.uppercase() ?: "US"
}

@Suppress("DEPRECATION")
fun countryName(code: String): String =
    runCatching { Locale("", code).getDisplayCountry(Locale. ENGLISH) }.getOrNull()?.ifBlank { null } ?: code

/**
 * Radio Browser (radio-browser.info): a free, open directory of ~50,000 stations. Several mirrors
 * share the load; we stick to whichever answered last.
 */
object RadioBrowser {
    private val hosts = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info",
        "de2.api.radio-browser.info", "fi1.api.radio-browser.info")
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    @Volatile private var good: String? = null

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (host in listOfNotNull(good) + hosts.filter { it != good }) {
            try {
                val url = "https://$host/json/$path".toHttpUrl().newBuilder()
                    .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                http.newCall(Request.Builder().url(url).header("User-Agent", "Fidelis/1.3").build()).execute().use { r ->
                    if (r.isSuccessful) { good = host; return@withContext r.body?.string().orEmpty() }
                }
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Radio directory unavailable")
    }

    private suspend fun stations(params: Map<String, String>): List<Station> {
        val defaults = mapOf("hidebroken" to "true", "order" to "clickcount", "reverse" to "true")
        val arr = JSONArray(get("stations/search", defaults + params))
        return (0 until arr.length()).mapNotNull { Station.from(arr.getJSONObject(it)) }.distinctBy { it.name.lowercase() }
    }

    suspend fun top(limit: Int = 40) = stations(mapOf("limit" to "$limit"))
    suspend fun loved(limit: Int = 14) = stations(mapOf("order" to "votes", "limit" to "$limit"))
    suspend fun country(code: String, limit: Int = 80) = stations(mapOf("countrycode" to code, "limit" to "$limit"))
    suspend fun tag(tag: String, limit: Int = 80) = stations(mapOf("tag" to tag, "limit" to "$limit"))
    suspend fun search(q: String) = stations(mapOf("name" to q, "limit" to "60"))

    suspend fun countries(): List<RadioCountry> {
        val arr = JSONArray(get("countries", mapOf("order" to "stationcount", "reverse" to "true", "hidebroken" to "true")))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { RadioCountry(it.optString("iso_3166_1").uppercase(), it.optString("name"), it.optInt("stationcount")) }
            .filter { it.code.length == 2 && it.stations >= 5 }
            .distinctBy { it.code }
    }

    /** Tells the directory a station was played, which keeps its popular lists honest. */
    suspend fun click(id: String) { runCatching { get("url/$id") } }
}

/** Your favourite and recently played stations, kept on the phone. */
object RadioLibrary {
    var favorites by mutableStateOf<List<Station>>(emptyList()); private set
    var recents by mutableStateOf<List<Station>>(emptyList()); private set
    private var loaded = false
    private const val PREFS = "fidelis_radio"

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, 0)
        favorites = read(p.getString("favorites", null))
        recents = read(p.getString("recents", null))
    }

    fun isFavorite(s: Station) = favorites.any { it.id == s.id }

    fun toggleFavorite(context: Context, s: Station) {
        favorites = if (isFavorite(s)) favorites.filter { it.id != s.id } else listOf(s) + favorites
        save(context, "favorites", favorites)
    }

    fun played(context: Context, s: Station) {
        recents = (listOf(s) + recents.filter { it.id != s.id }).take(12)
        save(context, "recents", recents)
    }

    private fun read(json: String?): List<Station> {
        if (json == null) return emptyList()
        return runCatching {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { Station.from(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, key: String, list: List<Station>) {
        val a = JSONArray().apply { list.forEach { put(it.toJson()) } }
        context.getSharedPreferences(PREFS, 0).edit().putString(key, a.toString()).apply()
    }
}
