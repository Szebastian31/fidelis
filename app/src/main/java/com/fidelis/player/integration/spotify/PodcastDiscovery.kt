package com.fidelis.player.integration.spotify

import android.content.Context
import android.telephony.TelephonyManager
import com.fidelis.player.radio.RadioBrowser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json. JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A suggested show. spotifyId is null for shows found on Apple Podcasts, until they're looked up on Spotify. */
data class PodcastPick(val key: String, val name: String, val by: String, val imageUrl: String?, val spotifyId: String? = null)
data class PodcastRow(val key: String, val eyebrow: String, val title: String, val shows: List<PodcastPick>)
data class PodcastPicks(val vibe: List<String>, val rows: List<PodcastRow>)

/** Apple Podcasts' main genres and their ids, used for its charts. */
val PODCAST_GENRES = linkedMapOf(
    "Comedy" to 1303, "True Crime" to 1488, "News" to 1489, "Society & Culture" to 1324, "History" to 1487,
    "Science" to 1533, "Technology" to 1318, "Business" to 1321, "Sports" to 1545, "Music" to 1310,
    "Health & Fitness" to 1512, "Education" to 1304, "Fiction" to 1483, "Arts" to 1301, "TV & Film" to 1309,
    "Kids & Family" to 1305, "Leisure" to 1502, "Religion & Spirituality" to 1314,
)

/** Names reduced to letters and digits, to tell whether two shows are the same. */
internal fun podKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * Finding new podcasts. Spotify has no podcast charts or suggestions in its API, so those come from
 * Apple Podcasts' public charts and search, and Spotify is only asked to play them.
 */
@Singleton
class PodcastDiscovery @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: SpotifyRepository,
) {
    private val prefs = context.getSharedPreferences("podcast_genres2", Context.MODE_PRIVATE)
    private val resolved = ConcurrentHashMap<String, String>()

    /** Your country, from the phone's network or SIM, so charts and search match where you are. */
    private val cc: String = run {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it != null && it.length == 2 }?.lowercase() ?: "us"
    }

    val countryName: String = runCatching { Locale.Builder().setRegion(cc.uppercase()).build().displayCountry }
        .getOrDefault("").ifBlank { "your country" }

    private fun enc(q: String) = URLEncoder.encode(q.trim().take(100), "UTF-8")

    private fun get(url: String): String? = runCatching {
        RadioBrowser.http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** Spotify's show search. Spotify no longer accepts market=from_token, so the country is passed directly. */
    private suspend fun spotify(q: String, pages: Int = 2): List<PodcastPick> = coroutineScope {
        val e = enc(q)
        (0 until pages).map { p ->
            async(Dispatchers.IO) {
                val items = repo.getJson("https://api.spotify.com/v1/search?q=$e&type=show&limit=10&offset=${p * 10}&market=${cc.uppercase()}")
                    ?.optJSONObject("shows")?.optJSONArray("items")
                buildList { if (items != null) for (i in 0 until items.length()) items.optJSONObject(i)?.spotifyPick()?.let { add(it) } }
            }
        }.awaitAll().flatten().distinctBy { it.key }
    }

    private suspend fun appleRaw(term: String, limit: Int): List<JSONObject>? = withContext(Dispatchers.IO) {
        val body = get("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=$limit&country=$cc&term=${enc(term)}")
            ?: return@withContext null
        val res = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull() ?: return@withContext null
        (0 until res.length()).mapNotNull { res.optJSONObject(it) }
    }

    private suspend fun apple(term: String, limit: Int = 25) = appleRaw(term, limit).orEmpty().mapNotNull { it.applePick() }

    /** Any show: Spotify first, so it plays straight away, then Apple Podcasts. A genre's name gives its chart. */
    suspend fun search(q: String): List<PodcastPick> {
        if (q.isBlank()) return emptyList()
        PODCAST_GENRES.keys.firstOrNull { it.equals(q.trim(), ignoreCase = true) }?.let { return chart(it, limit = 50) }
        return spotify(q).ifEmpty { apple(q) }.distinctBy { it.key }
    }

    /** Apple Podcasts' top shows, overall or in one genre. */
    suspend fun chart(genre: String? = null, country: String = cc, limit: Int = 25): List<PodcastPick> = withContext(Dispatchers.IO) {
        val g = genre?.let { PODCAST_GENRES[it] }?.let { "/genre=$it" }.orEmpty()
        val body = get("https://itunes.apple.com/$country/rss/toppodcasts/limit=$limit$g/json") ?: return@withContext emptyList()
        val feed = runCatching { JSONObject(body).optJSONObject("feed") }.getOrNull() ?: return@withContext emptyList()
        val entries = feed.optJSONArray("entry") ?: feed.optJSONObject("entry")?.let { JSONArray().put(it) } ?: return@withContext emptyList()
        (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val name = e.optJSONObject("im:name")?.optString("label").orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val imgs = e.optJSONArray("im:image")
            val img = imgs?.optJSONObject(imgs.length() - 1)?.optString("label")
                ?.replace(Regex("/\\d+x\\d+bb\\.png$"), "/600x600bb.jpg")?.ifBlank { null }
            val id = e.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id").orEmpty()
            PodcastPick("a$id", name, e.optJSONObject("im:artist")?.optString("label").orEmpty(), img)
        }.distinctBy { it.key }
    }

    /** The show's main genre, such as "Comedy", kept on the phone. Null when Apple doesn't know the show. */
    suspend fun genre(s: PodcastShow): String? {
        prefs.getString(s.id, null)?.let { return it.ifBlank { null } }
        val res = appleRaw(s.name, 5) ?: return null
        val want = podKey(s.name)
        val hit = res.firstOrNull { o ->
            val got = podKey(o.optString("collectionName"))
            want.isNotEmpty() && got.isNotEmpty() && (got == want || got.startsWith(want) || want.startsWith(got))
        }
        val names = hit?.optJSONArray("genres")
        val g = names?.let { a -> (0 until a.length()).map { a.optString(it) }.firstOrNull { it in PODCAST_GENRES } }
        prefs.edit().putString(s.id, g.orEmpty()).apply()
        return g
    }

    /** The artists you've played most lately. */
    private suspend fun topArtists(): List<String> = withContext(Dispatchers.IO) {
        val items = repo.getJson("https://api.spotify.com/v1/me/top/artists?limit=8&time_range=short_term")?.optJSONArray("items")
        if (items == null || items.length() == 0)
            return@withContext repo.fetchTopTracks("short_term", 30).map { it.artist }.filter { it.isNotBlank() }.distinct().take(8)
        (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optString("name")?.ifBlank { null } }
    }

    /** Rows of shows picked from what you listen to. Shows you already follow never appear. */
    suspend fun forYou(shows: List<PodcastShow>, favourite: PodcastShow?): PodcastPicks = coroutineScope {
        val tagged = shows.take(15).map { s -> async { s to genre(s) } }
        val artists = async { runCatching { topArtists() }.getOrDefault(emptyList()) }
        val home = async { chart() }
        val world = async { chart(country = if (cc == "us") "gb" else "us") }
        val t = tagged.awaitAll()
        val vibe = t.mapNotNull { it.second }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }.take(4)
        val fav = favourite ?: shows.firstOrNull()
        val favGenre = fav?.let { f -> t.firstOrNull { it.first.id == f.id }?.second }
        val like = async { favGenre?.let { chart(it, limit = 50) }.orEmpty() }
        val moods = vibe.filter { it != favGenre }.take(2).map { g -> async { g to chart(g, limit = 50) } }
        val about = async {
            artists.await().take(6).filter { podKey(it).length >= 3 }.map { a ->
                async { a to apple(a, 15).filter { podKey(it.name).contains(podKey(a)) } }
            }.awaitAll().filter { it.second.isNotEmpty() }
        }

        val seen = shows.map { podKey(it.name) }.toHashSet()
        fun fresh(l: List<PodcastPick>) = l.filter { seen.add(podKey(it.name)) }.take(15)
        val rows = mutableListOf<PodcastRow>()
        if (fav != null) fresh(like.await()).takeIf { it.isNotEmpty() }?.let { rows += PodcastRow("like", "Because you listen to", fav.name, it) }
        about.await().let { hits ->
            fresh(hits.flatMap { it.second }).takeIf { it.isNotEmpty() }?.let {
                rows += PodcastRow("artists", "About the artists you play", hits.take(3).joinToString(", ") { h -> h.first }, it)
            }
        }
        fresh(home.await()).takeIf { it.isNotEmpty() }?.let { rows += PodcastRow("home", "Charts", "Top in $countryName", it) }
        moods.awaitAll().forEach { (g, l) ->
            fresh(l).takeIf { it.isNotEmpty() }?.let { rows += PodcastRow("vibe$g", "Your vibe", "More $g", it) }
        }
        fresh(world.await()).takeIf { it.isNotEmpty() }?.let { rows += PodcastRow("world", "Charts", "Top worldwide", it) }
        PodcastPicks(vibe, rows)
    }

    /** The Spotify show for a pick, matched by name. Null when Spotify doesn't have it. */
    suspend fun resolve(p: PodcastPick): String? {
        p.spotifyId?.let { return it }
        resolved[p.key]?.let { return it }
        val hits = spotify(p.name, pages = 1)
        val want = podKey(p.name)
        val best = hits.firstOrNull { podKey(it.name) == want }
            ?: hits.firstOrNull { val k = podKey(it.name); k.isNotEmpty() && (k.startsWith(want) || want.startsWith(k)) }
            ?: hits.firstOrNull()
        return best?.spotifyId?.also { resolved[p.key] = it }
    }
}

private fun JSONObject.spotifyPick(): PodcastPick? {
    val id = optString("id").ifBlank { return null }
    val img = optJSONArray("images")?.optJSONObject(0)?.optString("url")?.ifBlank { null }
    return PodcastPick(id, optString("name"), optString("publisher"), img, id)
}

private fun JSONObject.applePick(): PodcastPick? {
    val name = optString("collectionName").ifBlank { return null }
    val img = optString("artworkUrl600").ifBlank { optString("artworkUrl100") }.ifBlank { null }
    return PodcastPick("a" + optLong("collectionId"), name, optString("artistName"), img)
}
