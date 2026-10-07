package com.fidelis.player.data.settings

import android.content.Context
import com.fidelis.player.playback.crossfade.CrossfadeEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which copy to play when you own a track in more than one format. */
enum class QualityPref(val label: String) {
    LOSSLESS_ONLY("Lossless only"),
    LOSSLESS_FIRST("Lossless first"),
    ANY("Any file"),
}

/** Default crossfade; maps onto CrossfadeEngine.userPreferenceMs. */
enum class CrossfadePref(val label: String, val ms: Int) {
    AUTO("Auto", CrossfadeEngine.AUTO),
    OFF("Off", CrossfadeEngine.OFF),
    SHORT("Short", 2_000),
    MEDIUM("Medium", 6_000),
    LONG("Long", 11_000);

    companion object {
        fun fromMs(ms: Int) = entries.firstOrNull { it.ms == ms } ?: AUTO
    }
}

@Singleton
class PlaybackSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("fidelis_playback", Context.MODE_PRIVATE)

    private val _quality = MutableStateFlow(
        runCatching { QualityPref.valueOf(prefs.getString("quality", "") ?: "") }
            .getOrDefault(QualityPref.LOSSLESS_FIRST)
    )
    val quality: StateFlow<QualityPref> = _quality.asStateFlow()

    private val _crossfade = MutableStateFlow(
        CrossfadePref.fromMs(prefs.getInt("crossfade_ms", CrossfadeEngine.AUTO))
    )
    val crossfade: StateFlow<CrossfadePref> = _crossfade.asStateFlow()

    val crossfadeMs: Int get() = _crossfade.value.ms

    fun setQuality(q: QualityPref) {
        prefs.edit().putString("quality", q.name).apply()
        _quality.value = q
    }

    fun setCrossfade(c: CrossfadePref) {
        prefs.edit().putInt("crossfade_ms", c.ms).apply()
        _crossfade.value = c
    }
}
