package com.fidelis.player.integration.spotify

import android.graphics.Bitmap
import coil.map.Mapper
import coil.request.Options

/** Spotify's image ids start with a size code. Small ones are swapped for the 640 px version. */
private val SPOTIFY_SIZES = listOf(
    "ab67616d00001e02" to "ab67616d0000b273", "ab67616d00004851" to "ab67616d0000b273", // albums
    "ab67656300005f1f" to "ab6765630000ba8a", "ab6765630000f68d" to "ab6765630000ba8a", // shows and episodes
    "ab67616100005174" to "ab6761610000e5eb", "ab6761610000f178" to "ab6761610000e5eb", // artists
)
private val MOSAIC = Regex("""mosaic\.scdn\.co/\d+/""")
private val APPLE = Regex("""/\d+x\d+bb\.(jpg|png)$""")

/** The sharpest version of a cover link from Spotify or Apple Podcasts. Other links are left as they are. */
fun spotifyHiRes(url: String): String = when {
    "i.scdn.co/image/" in url -> SPOTIFY_SIZES.fold(url) { u, (small, big) -> u.replace("/image/$small", "/image/$big") }
    "mosaic.scdn.co/" in url -> url.replace(MOSAIC, "mosaic.scdn.co/640/")
    "mzstatic.com" in url -> url.replace(APPLE, "/600x600bb.jpg")
    else -> url
}

/** Used for every image Fidelis loads, so a thumbnail link never shows up blurry. */
object SpotifyHiRes : Mapper<String, String> {
    override fun map(data: String, options: Options): String? = spotifyHiRes(data).takeIf { it != data }
}

/** A smaller copy for the system's media card and notification. Big covers can be too large to hand over. */
fun Bitmap.forSystem(max: Int = 360): Bitmap {
    if (width <= max && height <= max && config != Bitmap.Config.HARDWARE) return this
    val src = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val k = max.toFloat() / maxOf(src.width, src.height)
    if (k >= 1f) return src
    return Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
}
