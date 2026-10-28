package com.fidelis.player.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SoundPreset(val label: String, val gains: List<Float>) {
    FLAT("Flat", List(10) { 0f }),
    CLARITY("Clarity", listOf(0f, 0f, -1f, -1f, 0f, 0f, 1f, 2.5f, 3f, 2f)),
    WARMTH("Warmth", listOf(2f, 2.5f, 2f, 1f, 0f, 0f, -0.5f, -1f, -1f, -1.5f)),
    BASS("Bass", listOf(5f, 4.5f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
    VOCAL("Vocal", listOf(-2f, -1.5f, -1f, 0f, 1f, 2f, 3f, 2f, 0f, -1f)),
    LATE_NIGHT("Late night", listOf(-3f, -2f, -1f, 0f, 0.5f, 1f, 1f, 0f, -1.5f, -3f)),
    CUSTOM("Custom", List(10) { 0f }),
}

/**
 * Measured corrections for the FreeBuds Pro 4: AutoEq's fit of Regan Cipher's measurements
 * (published through OPRA, CC BY-SA 4.0). They even out the earbuds' own tuning; your EQ sits on top.
 */
enum class Correction(val label: String, private val filters: List<Biquad>) {
    OFF("Off", emptyList()),
    ANC_ON("ANC on", listOf(
        Biquad.lowShelf(105f, -5.0f, 0.70f), Biquad.peak(83f, 5.8f, 0.62f),
        Biquad.peak(400f, -3.4f, 0.73f), Biquad.peak(1230f, 1.6f, 0.57f),
        Biquad.peak(5616f, -3.9f, 2.97f), Biquad.highShelf(10000f, -8.1f, 0.70f),
        Biquad.peak(8787f, 2.6f, 2.09f), Biquad.peak(2925f, -0.9f, 3.01f),
        Biquad.peak(10000f, 1.3f, 4.01f), Biquad.peak(3810f, 0.9f, 2.93f),
    )),
    ANC_OFF("ANC off", listOf(
        Biquad.lowShelf(105f, -1.3f, 0.70f), Biquad.peak(374f, -2.9f, 1.64f),
        Biquad.peak(86f, 1.8f, 0.99f), Biquad.peak(3719f, 2.2f, 2.64f),
        Biquad.peak(29f, -1.4f, 1.74f), Biquad.highShelf(10000f, -7.1f, 0.70f),
        Biquad.peak(5958f, -2.4f, 4.37f), Biquad.peak(9160f, 1.7f, 2.13f),
        Biquad.peak(2311f, 1.0f, 4.84f), Biquad.peak(4481f, 1.3f, 5.61f),
    ));

    fun gainAt(hz: Float): Float = filters.fold(0f) { acc, f -> acc + f.dbAt(hz) }
}

data class SoundSettings(
    val enabled: Boolean = false,
    val preset: SoundPreset = SoundPreset.FLAT,
    val gains: List<Float> = SoundPreset.FLAT.gains,
    val boost: Float = 0f,
    val correction: Correction = Correction.OFF,
)

/** Where the sound is going; each output keeps its own settings. */
data class SoundOutput(val key: String, val label: String) {
    /** Only the FreeBuds Pro 4 have a measured correction so far. */
    val hasCorrection: Boolean get() = label.contains("FreeBuds Pro 4", ignoreCase = true)
}

/** Shapes Spotify's audio session: 10-band EQ, headphone correction, loudness boost and a limiter. */
object SoundEngine {
    val CENTERS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    private val CUTOFFS = FloatArray(10) { i -> if (i == 9) 20000f else sqrt(CENTERS[i] * CENTERS[i + 1]) }
    // The correction runs on its own 31 third-octave bands, after your EQ.
    private val POST = FloatArray(31) { 20f * 2f.pow(it / 3f) }
    private val POST_CUTOFFS = FloatArray(31) { i -> if (i == 30) 22000f else POST[i] * 2f.pow(1f / 6f) }
    const val MAX_DB = 12f
    const val MAX_BOOST = 6f
    private const val PRIORITY = 1000
    private const val KEY_SESSION = "spotify_session"
    private val BT = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    private val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)
    private val SPEAKER = SoundOutput("speaker", "Phone speaker")

    private val _settings = MutableStateFlow(SoundSettings())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()
    private val _device = MutableStateFlow(SPEAKER)
    val device: StateFlow<SoundOutput> = _device.asStateFlow()
    private val _status = MutableStateFlow("Off")
    val status: StateFlow<String> = _status.asStateFlow()

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session = 0
    private var loaded = false
    private var dpBroken = false
    private var dp: Any? = null // DynamicsProcessing, kept untyped for older Android
    private var eq: Equalizer? = null
    private val bandCache = HashMap<Correction, FloatArray>()

    private fun prefs() = app!!.getSharedPreferences("fidelis_sound", Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        session = prefs().getInt(KEY_SESSION, 0)
        app!!.getSystemService(AudioManager::class.java).registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshDevice()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshDevice()
        }, Handler(Looper.getMainLooper()))
        refreshDevice()
        scope.launch { SoundSession.spotify.collect { s -> if (s != null) attach(s.session) } }
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun applyPreset(p: SoundPreset) {
        if (p == SoundPreset.CUSTOM) return
        update { it.copy(enabled = true, preset = p, gains = p.gains) }
    }
    fun setBand(i: Int, db: Float) = update {
        it.copy(preset = SoundPreset.CUSTOM, gains = it.gains.toMutableList().also { g -> g[i] = db.coerceIn(-MAX_DB, MAX_DB) })
    }
    fun setBoost(db: Float) = update { it.copy(boost = ((db * 2f).roundToInt() / 2f).coerceIn(0f, MAX_BOOST)) }
    fun setCorrection(c: Correction) = update { it.copy(enabled = true, correction = c) }

    private fun update(change: (SoundSettings) -> SoundSettings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        save()
        apply()
    }

    private fun attach(id: Int) {
        if (id == session && (dp != null || eq != null)) return
        release()
        session = id
        app?.let { prefs().edit().putInt(KEY_SESSION, id).apply() }
        apply()
    }

    private fun refreshDevice() {
        val am = app?.getSystemService(AudioManager::class.java) ?: return
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val pick = outs.firstOrNull { it.type in BT } ?: outs.firstOrNull { it.type in WIRED }
        val out = if (pick == null) SPEAKER else {
            val name = pick.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: if (pick.type in BT) "Bluetooth headphones" else "Wired headphones"
            SoundOutput("dev:$name", name)
        }
        if (loaded && out == _device.value) return
        loaded = true
        _device.value = out
        _settings.value = load(out)
        apply()
    }

    private fun load(out: SoundOutput): SoundSettings {
        val raw = prefs().getString(out.key, null) ?: return defaults(out)
        return runCatching {
            val p = raw.split('|')
            val gains = p[3].split(',').map { it.toFloat() }
            require(gains.size == 10)
            // 1.3 had a hand-tuned "FreeBuds Pro 4" preset; the measured correction replaces it.
            val old = p[1] == "FREEBUDS_PRO_4"
            SoundSettings(
                enabled = p[0] == "1",
                preset = if (old) SoundPreset.FLAT else SoundPreset.valueOf(p[1]),
                gains = if (old) SoundPreset.FLAT.gains else gains,
                boost = p[2].toFloat(),
                correction = when {
                    !out.hasCorrection -> Correction.OFF
                    old -> Correction.ANC_ON
                    else -> p.getOrNull(4)?.let { n -> Correction.values().firstOrNull { it.name == n } } ?: Correction.ANC_ON
                },
            )
        }.getOrElse { defaults(out) }
    }

    private fun defaults(out: SoundOutput) =
        SoundSettings(correction = if (out.hasCorrection) Correction.ANC_ON else Correction.OFF)

    private fun save() {
        val s = _settings.value
        prefs().edit().putString(
            _device.value.key,
            "${if (s.enabled) 1 else 0}|${s.preset.name}|${s.boost}|${s.gains.joinToString(",")}|${s.correction.name}",
        ).apply()
    }

    private fun apply() {
        if (app == null) return
        val s = _settings.value
        if (!s.enabled) {
            if (Build.VERSION.SDK_INT >= 28) (dp as? DynamicsProcessing)?.let { runCatching { it.enabled = false } }
            eq?.let { runCatching { it.enabled = false } }
            _status.value = "Off · Spotify plays untouched"
            return
        }
        if (session == 0) { _status.value = "Waiting for Spotify · play a song to start"; return }
        if (Build.VERSION.SDK_INT >= 28 && !dpBroken) {
            if (runCatching { applyDp(s) }.isSuccess) return
            dpBroken = true
            release()
        }
        if (runCatching { applyEq(s) }.isFailure) {
            release()
            _status.value = "Spotify's sound is out of reach · play a song to retry"
        }
    }

    private fun correctionBands(c: Correction) = bandCache.getOrPut(c) {
        FloatArray(POST.size) { c.gainAt(POST[it]).coerceIn(-MAX_DB, MAX_DB) }
    }

    @RequiresApi(28)
    private fun applyDp(s: SoundSettings) {
        eq?.let { runCatching { it.release() } }; eq = null
        val fx = (dp as? DynamicsProcessing) ?: DynamicsProcessing(
            PRIORITY, session,
            DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                true, CENTERS.size, false, 0, true, POST.size, true,
            ).build(),
        ).also { dp = it }
        val pre = DynamicsProcessing.Eq(true, true, CENTERS.size)
        for (i in CENTERS.indices) pre.setBand(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], s.gains[i]))
        fx.setPreEqAllChannelsTo(pre)
        val fix = correctionBands(s.correction)
        val post = DynamicsProcessing.Eq(true, true, POST.size)
        for (i in POST.indices) post.setBand(i, DynamicsProcessing.EqBand(true, POST_CUTOFFS[i], fix[i]))
        fx.setPostEqAllChannelsTo(post)
        // Limiter just under full scale: attack 1 ms, release 60 ms, ratio 10:1, threshold -1 dB.
        fx.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
        // Lower the input by the biggest boosts so they can't clip, then add the user's boost.
        val headroom = (s.gains.maxOrNull() ?: 0f).coerceAtLeast(0f) + (fix.maxOrNull() ?: 0f).coerceAtLeast(0f)
        fx.setInputGainAllChannelsTo(s.boost - headroom)
        fx.enabled = true
        _status.value = if (s.correction == Correction.OFF) "Shaping Spotify's sound · 10 bands + limiter"
            else "Shaping Spotify's sound · 10 bands + FreeBuds correction + limiter"
    }

    private fun applyEq(s: SoundSettings) {
        val fx = eq ?: Equalizer(PRIORITY, session).also { eq = it }
        val lo = fx.bandLevelRange[0].toInt()
        val hi = fx.bandLevelRange[1].toInt()
        for (b in 0 until fx.numberOfBands) {
            val hz = fx.getCenterFreq(b.toShort()) / 1000f
            val db = gainAt(s.gains, hz) + s.correction.gainAt(hz)
            fx.setBandLevel(b.toShort(), (db * 100f).roundToInt().coerceIn(lo, hi).toShort())
        }
        fx.enabled = true
        _status.value = "Shaping Spotify's sound · ${fx.numberOfBands}-band mode (no boost)"
    }

    /** Reads the 10-band curve at any frequency, so a 5-band phone EQ can follow it. */
    private fun gainAt(g: List<Float>, hz: Float): Float {
        if (hz <= CENTERS.first()) return g.first()
        if (hz >= CENTERS.last()) return g.last()
        val i = CENTERS.indexOfLast { it <= hz }
        val t = ln(hz / CENTERS[i]) / ln(CENTERS[i + 1] / CENTERS[i])
        return g[i] + (g[i + 1] - g[i]) * t
    }

    private fun release() {
        if (Build.VERSION.SDK_INT >= 28) (dp as? DynamicsProcessing)?.let { runCatching { it.release() } }
        dp = null
        eq?.let { runCatching { it.release() } }
        eq = null
    }
}
