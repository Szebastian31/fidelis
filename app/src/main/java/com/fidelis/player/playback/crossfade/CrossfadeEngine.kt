package com.fidelis.player.playback.crossfade

import com.fidelis.player.data.db.AudioFeaturesEntity
import kotlin.math.abs

/**
 * Intelligent crossfade.
 *
 * The blend length is chosen from the MUSIC, not a fixed slider:
 *   - tempos close AND keys harmonically compatible  -> long, beat-matched blend
 *   - tempos close but keys clash                     -> medium blend
 *   - tempos far apart                                -> short cut
 *
 * Harmonic compatibility uses the Camelot wheel: same code, +/-1 on the wheel, or the
 * relative major/minor (same number, A<->B) are considered compatible.
 *
 * Implementation note: Media3 gives gapless for free but no crossfade. Realize this plan
 * with TWO ExoPlayer instances (A = outgoing, B = incoming) and an equal-power volume ramp
 * driven on the main thread — see [CrossfadePlan] + the ramp loop in PlaybackController.
 */
class CrossfadeEngine(private val userPreferenceMs: Int = AUTO) {

    companion object {
        const val AUTO = -1
        const val OFF = 0
        private const val MAX_BLEND_MS = 11_000
        private const val MED_BLEND_MS = 6_000
        private const val MIN_BLEND_MS = 2_000
        private const val BPM_CLOSE = 4f
        private const val BPM_NEAR = 12f
    }

    data class CrossfadePlan(
        val blendMs: Int,
        val beatMatched: Boolean,
        val reason: String,
    )

    fun plan(outgoing: AudioFeaturesEntity?, incoming: AudioFeaturesEntity?): CrossfadePlan {
        if (userPreferenceMs == OFF) return CrossfadePlan(0, false, "crossfade off")
        if (userPreferenceMs > 0) return CrossfadePlan(userPreferenceMs, false, "fixed ${userPreferenceMs / 1000}s")

        // AUTO: need features on both sides; otherwise a safe medium blend.
        if (outgoing == null || incoming == null)
            return CrossfadePlan(MED_BLEND_MS, false, "no analysis; default blend")

        val dBpm = abs(outgoing.bpm - incoming.bpm)
        val harmonic = compatible(outgoing.camelotKey, incoming.camelotKey)

        return when {
            dBpm <= BPM_CLOSE && harmonic -> CrossfadePlan(
                MAX_BLEND_MS, beatMatched = true,
                reason = "tempos align (${outgoing.bpm.toInt()}->${incoming.bpm.toInt()} BPM), keys compatible",
            )
            dBpm <= BPM_CLOSE -> CrossfadePlan(
                MED_BLEND_MS, beatMatched = true,
                reason = "tempos align but keys clash",
            )
            dBpm <= BPM_NEAR -> CrossfadePlan(
                MED_BLEND_MS, beatMatched = false,
                reason = "near tempos (${outgoing.bpm.toInt()}->${incoming.bpm.toInt()} BPM)",
            )
            else -> CrossfadePlan(
                MIN_BLEND_MS, beatMatched = false,
                reason = "tempos clash; quick cut",
            )
        }
    }

    /** Equal-power gain for position [t] in [0,1] of the blend (keeps perceived loudness flat). */
    fun gainsAt(t: Float): Pair<Float, Float> {
        val x = t.coerceIn(0f, 1f)
        val outGain = kotlin.math.cos(x * Math.PI.toFloat() / 2f)
        val inGain = kotlin.math.sin(x * Math.PI.toFloat() / 2f)
        return outGain to inGain
    }

    // Camelot: "8A", "12B", etc. Compatible = same, neighbor on wheel, or relative major/minor.
    private fun compatible(a: String, b: String): Boolean {
        val pa = parse(a) ?: return false
        val pb = parse(b) ?: return false
        if (pa == pb) return true
        if (pa.first == pb.first && pa.second != pb.second) return true // relative major/minor
        if (pa.second == pb.second) {
            val diff = ((pa.first - pb.first + 12) % 12)
            if (diff == 1 || diff == 11) return true                    // +/-1 on the wheel
        }
        return false
    }

    private fun parse(code: String): Pair<Int, Char>? {
        val m = Regex("^(\\d{1,2})([AB])$").find(code.trim().uppercase()) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2][0]
    }
}
