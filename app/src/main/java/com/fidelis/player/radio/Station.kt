package com.fidelis.player.radio

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val UA = "Fidelis/1.4"
/** Ids of stations found through Radio Garden start with this. */
private const val GARDEN = "rg:"

/** One live station, from the Radio Browser directory or Radio Garden. */
data class Station(
    val id: String, val name: String, val stream: String, val favicon: String,
    val tags: List<String>, val country: String, val countryCode: String,
    val codec: String, val bitrate: Int, val hls: Boolean, val place: String = "",
) {
    val fromGarden: Boolean get() = id.startsWith(GARDEN)

    /** "Kameyama, Japan · Pop · 128 kbps" */
    val meta: String get() {
        val land = if (countryCode.length == 2) countryName(countryCode) else country
        val where = if (place.isNotBlank() && land.isNotBlank()) "$place, $land" else land
        return listOfNotNull(
            where.ifBlank { null },
            tags.firstOrNull()?.replaceFirstChar { it.titlecase() },
            bitrate.takeIf { it > 0 }?.let { "$it kbps" },
        ).joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject().put("stationuuid", id).put("name", name).put("url_resolved", stream)
        .put("favicon", favicon).put("tags", tags.joinToString(",")).put("country", country)
        .put("countrycode", countryCode).put("codec", codec).put("bitrate", bitrate).put("hls", if (hls) 1 else 0)
        .put("place", place)

    companion object {
        fun from(o: JSONObject): Station? {
            val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name").trim()
            if (stream.isBlank() || name.isBlank()) return null
            return Station(
                o.optString("stationuuid"), name, stream, o.optString("favicon").takeIf { it.startsWith("http") }.orEmpty(),
                o.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }.take(4),
                o.optString("country"), o.optString("countrycode"), o.optString("codec"),
                o.optInt("bitrate"), o.optInt("hls") == 1, o.optString("place"),
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
    runCatching { Locale("", code).getDisplayCountry(Locale.ENGLISH) }.getOrNull()?.ifBlank { null } ?: code

private val MARKS = Regex("\\p{M}+")

/** Lower case without accents, so "radio" finds "Rádio". */
internal fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase().trim()

/**
 * Search the directory and Radio Garden together. Exact names come first, then names with every
 * word you typed, then the rest.
 */
suspend fun searchEverywhere(q: String): List<Station> = coroutineScope {
    val dir = async { runCatching { RadioBrowser.search(q) }.getOrDefault(emptyList()) }
    val garden = async { runCatching { RadioGarden.search(q) }.getOrDefault(emptyList()) }
    val want = fold(q)
    val words = want.split(' ').filter { it.isNotBlank() }
    (dir.await() + garden.await())
        .distinctBy { fold(it.name) + it.countryCode.uppercase() }
        .sortedByDescending { s ->
            val n = fold(s.name)
            when { n == want -> 3; words.all { it in n } -> 2; words.any { it in n } -> 1; else -> 0 }
        }
}

/**
 * Radio Browser (radio-browser.info): a free, open directory of ~50,000 stations. Several mirrors
 * share the load; we stick to whichever answered last.
 */
object RadioBrowser {
    private val hosts = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info",
        "de2.api.radio-browser.info", "fi1.api.radio-browser.info")
    internal val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    @Volatile private var good: String? = null

    /** Hand-picked stations, each checked to be online, with tidy names. */
    private val PICKS = listOf(
        "9617a958-0601-11e8-ae97-52543be04c81" to "Radio Paradise",
        "961e6cac-0601-11e8-ae97-52543be04c81" to "NTS Radio 1",
        "932eb148-e6f6-11e9-a96c-52543be04c81" to "FIP",
        "445cbb3a-1c4e-49aa-a268-f5b6acfa8f2e" to "KEXP Seattle",
        "e4e4d123-0616-11ea-afd6-52543be04c81" to "LISTEN.moe J-Pop",
        "960cf833-0601-11e8-ae97-52543be04c81" to "SomaFM Groove Salad",
        "9067dc39-4bf4-4364-bd6b-8020cff3e15d" to "Nightride FM Chillsynth",
        "1d730b8a-49e2-403d-870c-07b1e27be7fd" to "Jazz24",
        "963fb390-0601-11e8-ae97-52543be04c81" to "Radio Nova",
        "5b9ceedf-eb85-11e9-a96c-52543be04c81" to "FIP Jazz",
        "d88c0d24-adb2-11e9-88f4-52543be04c81" to "Radio Paradise Mellow",
        "a1c3d110-9136-4564-bd41-5ec1b97c41ac" to "Record Megamix",
        "43be83dc-340b-11e8-bb9b-52543be04c81" to "LISTEN.moe K-Pop",
    )

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (host in listOfNotNull(good) + hosts.filter { it != good }) {
            try {
                val url = "https://$host/json/$path".toHttpUrl().newBuilder()
                    .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                    if (r.isSuccessful) { good = host; return@withContext r.body?.string().orEmpty() }
                }
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Radio directory unavailable")
    }

    private fun parse(json: String): List<Station> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { Station.from(arr.getJSONObject(it)) }
    }

    private suspend fun stations(params: Map<String, String>): List<Station> {
        val defaults = mapOf("hidebroken" to "true", "order" to "clickcount", "reverse" to "true")
        return parse(get("stations/search", defaults + params)).distinctBy { it.name.lowercase() }
    }

    suspend fun top(limit: Int = 40) = stations(mapOf("limit" to "$limit"))
    suspend fun loved(limit: Int = 14) = stations(mapOf("order" to "votes", "limit" to "$limit"))
    suspend fun country(code: String, limit: Int = 80) = stations(mapOf("countrycode" to code, "limit" to "$limit"))
    suspend fun tag(tag: String, limit: Int = 80) = stations(mapOf("tag" to tag, "limit" to "$limit"))
    suspend fun search(q: String) = stations(mapOf("name" to q, "limit" to "60"))

    suspend fun picks(): List<Station> {
        val names = PICKS.toMap()
        val order = PICKS.map { it.first }
        val found = parse(get("stations/byuuid", mapOf("uuids" to order.joinToString(","))))
            .sortedBy { order.indexOf(it.id) }.map { s -> s.copy(name = names[s.id] ?: s.name) }
        return listOf(RadioGarden.MEGA_MIX_JAPAN) + found
    }

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

/**
 * Radio Garden's search, for the many stations the open directory doesn't list. It isn't an official
 * public API, so it may change; the app then simply falls back to the directory.
 */
object RadioGarden {
    private const val API = "https://radio.garden/api"

    val MEGA_MIX_JAPAN = station("3Zw4BXZR", "Rádio Mega Mix Japan", "JP", "Japan", "Kameyama")

    fun station(id: String, name: String, code: String, country: String, place: String) = Station(
        GARDEN + id, name.trim(), "$API/ara/content/listen/$id/channel.mp3", "", emptyList(),
        country, code.uppercase(), "", 0, false, place,
    )

    suspend fun search(q: String): List<Station> = withContext(Dispatchers.IO) {
        val url = "$API/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build()
        val body = RadioBrowser.http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute()
            .use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext emptyList()
        val hits = JSONObject(body).optJSONObject("hits")?.optJSONArray("hits") ?: return@withContext emptyList()
        (0 until hits.length()).mapNotNull { i ->
            val src = hits.getJSONObject(i).optJSONObject("_source") ?: return@mapNotNull null
            val page = src.optJSONObject("page")
            if (src.optString("type") != "channel" || page == null) return@mapNotNull null
            val id = page.optString("url").substringAfterLast('/')
            val title = page.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            station(id, title, src.optString("code"), page.optJSONObject("country")?.optString("title").orEmpty(),
                page.optJSONObject("place")?.optString("title").orEmpty())
        }
    }
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
