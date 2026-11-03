package com.fidelis.player.mood

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max

/** A named spot on the mood dial, and the Last.fm tags that sound like it. */
class MoodAnchor(
    val label: String, val blurb: String, val x: Float, val y: Float,
    val tags: Set<String>, val avoid: Set<String>,
)

/** Where each feeling sits: x runs dark (−1) to bright (1), y runs calm (−1) to hyped (1). */
val MOOD_ANCHORS = listOf(
    MoodAnchor("Melancholy", "Moody, emotional, late at night", -0.72f, -0.55f, Mood.MELANCHOLY.tags, Mood.MELANCHOLY.avoid),
    MoodAnchor("Chill", "Mellow, soft and unhurried", 0.38f, -0.68f, Mood.CHILL.tags, Mood.CHILL.avoid),
    MoodAnchor("Focus", "Instrumental and minimal", -0.12f, -0.22f, Mood.FOCUS.tags, Mood.FOCUS.avoid),
    MoodAnchor("Happy", "Feel-good, sunny and bright", 0.78f, 0.18f, Mood.HAPPY.tags, Mood.HAPPY.avoid),
    MoodAnchor("Party", "Dance-floor picks, club to disco", 0.5f, 0.78f, Mood.PARTY.tags, Mood.PARTY.avoid),
    MoodAnchor("Energetic", "Upbeat and loud, keeps you moving", -0.08f, 0.74f, Mood.ENERGETIC.tags, Mood.ENERGETIC.avoid),
    MoodAnchor("Intense", "Heavy, dark and driving", -0.78f, 0.72f,
        setOf("metal", "hard rock", "industrial", "techno", "dark techno", "drill", "trap", "aggressive", "heavy",
            "hardcore", "dubstep", "grunge", "post-punk", "darkwave", "nu metal", "phonk"),
        setOf("happy", "sleep", "calm", "acoustic", "soft", "relaxing")),
)

private val CALM_DARK = Color(0xFF2B2F7A)
private val CALM_BRIGHT = Color(0xFF1FA39A)
private val HYPED_DARK = Color(0xFFC0243F)
private val HYPED_BRIGHT = Color(0xFFFFA23F)
private val CENTRE = Color(0xFF7B5CE0)

/** The dial's colour at a spot: four corners blended, with violet in the middle. */
fun vibeColor(x: Float, y: Float): Color {
    val u = ((x + 1) / 2).coerceIn(0f, 1f)
    val v = ((y + 1) / 2).coerceIn(0f, 1f)
    val edge = lerp(lerp(CALM_DARK, CALM_BRIGHT, u), lerp(HYPED_DARK, HYPED_BRIGHT, u), v)
    val mid = 1 - max(abs(x), abs(y)).coerceIn(0f, 1f)
    return lerp(edge, CENTRE, mid * mid * 0.7f)
}

/** A spot on the mood dial. Songs are scored against the three nearest feelings, blended by distance. */
data class Vibe(val x: Float, val y: Float) {
    val weights: List<Pair<MoodAnchor, Float>> by lazy {
        val raw = MOOD_ANCHORS.map { a -> val dx = a.x - x; val dy = a.y - y; a to 1f / (dx * dx + dy * dy + 0.04f) }
            .sortedByDescending { it.second }.take(3)
        val sum = raw.sumOf { it.second.toDouble() }.toFloat()
        raw.map { it.first to it.second / sum }
    }

    val nearest: MoodAnchor get() = weights.first().first

    val tagWeights: Map<String, Float> by lazy {
        val m = HashMap<String, Float>()
        weights.forEach { (a, w) -> a.tags.forEach { t -> m[t] = (m[t] ?: 0f) + w } }
        m
    }

    /** Tags that clash with this spot, unless a nearby feeling wants them. */
    val avoid: Set<String> by lazy {
        weights.filter { it.second >= 0.25f }.flatMap { it.first.avoid }.filter { (tagWeights[it] ?: 0f) < 0.2f }.toSet()
    }

    /** Strong tags (listed first by Last.fm) weigh more; clashing tags count against. */
    fun score(tags: List<String>): Float {
        var s = 0f
        tags.forEachIndexed { i, t ->
            tagWeights[t]?.let { s += it * (12 - minOf(i, 11)) }
            if (t in avoid) s -= 8f
        }
        return s
    }

    val name: String get() {
        val e = when { y > 0.33f -> 2; y < -0.33f -> 0; else -> 1 }
        val b = when { x > 0.33f -> 2; x < -0.33f -> 0; else -> 1 }
        return NAMES[e][b]
    }

    val color: Color get() = vibeColor(x, y)

    fun encode() = "$x,$y"

    companion object {
        private val NAMES = listOf(
            listOf("Slow and heavy-hearted", "Calm and focused", "Soft and sunny"),
            listOf("Moody", "Easy-going", "Warm and upbeat"),
            listOf("Dark and driving", "High energy", "Euphoric"),
        )

        fun parse(s: String?): Vibe? {
            val p = s?.split(',') ?: return null
            if (p.size != 2) return null
            val x = p[0].toFloatOrNull() ?: return null
            val y = p[1].toFloatOrNull() ?: return null
            return Vibe(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
        }
    }
}
