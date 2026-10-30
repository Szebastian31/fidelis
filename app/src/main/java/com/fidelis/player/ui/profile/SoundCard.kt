package com.fidelis.player.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fidelis.player.audio.Correction
import com.fidelis.player.audio.SoundEngine
import com.fidelis.player.audio.SoundPreset
import com.fidelis.player.ui.theme.*
import kotlin.math.roundToInt

/** Equalizer, headphone correction and safe loudness boost for Spotify's sound, per headphone. */
@Composable
fun SoundCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SoundEngine.init(context) }
    val s by SoundEngine.settings.collectAsState()
    val device by SoundEngine.device.collectAsState()
    val status by SoundEngine.status.collectAsState()

    SectionLabel("Sound")
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tuned for ${device.label}", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(status, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { SoundEngine.setEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Fidelis.purple, checkedThumbColor = Color.White),
                )
            }
            if (device.hasCorrection) {
                Spacer(Modifier.height(16.dp))
                Text("Headphone correction", color = Fidelis.ink, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Correction.values().forEach { c ->
                        FidelisChip(c.label, s.enabled && s.correction == c, { SoundEngine.setCorrection(c) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "A measured curve for the FreeBuds Pro 4 (AutoEq, from Regan Cipher's measurements). " +
                        "Pick the one that matches your noise-cancelling mode; presets and sliders sit on top.",
                    color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundPreset.values().filter { it != SoundPreset.CUSTOM || s.preset == SoundPreset.CUSTOM }.forEach { p ->
                    FidelisChip(p.label, s.enabled && s.preset == p, { SoundEngine.applyPreset(p) })
                }
            }
            Spacer(Modifier.height(18.dp))
            if (s.enabled) {
                Text(s.preset.desc, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().height(190.dp)) {
                SoundEngine.BAND_LABELS.forEachIndexed { i, label ->
                    BandSlider(s.gains[i], label, s.enabled, { SoundEngine.setBand(i, it) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loudness boost", color = Fidelis.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("%+.1f dB".format(s.boost), color = Fidelis.inkSoft, style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = s.boost,
                onValueChange = { SoundEngine.setBoost(it) },
                valueRange = 0f..SoundEngine.MAX_BOOST,
                enabled = s.enabled,
                colors = SliderDefaults.colors(thumbColor = Fidelis.pink, activeTrackColor = Fidelis.purple),
            )
            Text(
                "A limiter keeps every setting below clipping, so nothing distorts. If a preset sounds quieter, raise the boost.",
                color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun gainLabel(db: Float) = when {
    db == 0f -> "0"
    db % 1f == 0f -> "%+.0f".format(db)
    else -> "%+.1f".format(db)
}

@Composable
private fun BandSlider(db: Float, label: String, active: Boolean, onChange: (Float) -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(onChange)
    val top = Fidelis.pink
    val bottom = Fidelis.purple
    Column(modifier.alpha(if (active) 1f else 0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(gainLabel(db), color = Fidelis.inkSoft, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                val r = 7.dp.toPx()
                fun setFrom(y: Float) {
                    val f = 1f - ((y - r) / (size.height - 2 * r)).coerceIn(0f, 1f)
                    latest(((f * 2f - 1f) * SoundEngine.MAX_DB * 2f).roundToInt() / 2f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    setFrom(down.position.y)
                    drag(down.id) { it.consume(); setFrom(it.position.y) }
                }
            },
        ) {
            val r = 7.dp.toPx()
            val w = 4.dp.toPx()
            val cx = size.width / 2f
            val mid = size.height / 2f
            val y = mid - (db / SoundEngine.MAX_DB) * (mid - r)
            drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(cx - w / 2f, r), Size(w, size.height - 2 * r), CornerRadius(w))
            drawLine(Color.White.copy(alpha = 0.22f), Offset(cx - r, mid), Offset(cx + r, mid), 1.dp.toPx())
            val a = minOf(y, mid)
            val b = maxOf(y, mid)
            if (b - a > 0.5f) drawRoundRect(Brush.verticalGradient(listOf(top, bottom), a, b), Offset(cx - w / 2f, a), Size(w, b - a), CornerRadius(w))
            drawCircle(Color.White, r, Offset(cx, y))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
