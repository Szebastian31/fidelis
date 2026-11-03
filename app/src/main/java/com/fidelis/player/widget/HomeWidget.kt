package com.fidelis.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content. ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import coil.imageLoader
import coil.request.ImageRequest
import com.fidelis.player.MainActivity
import com.fidelis.player.R
import com.fidelis.player.playback.PlaybackService
import com.fidelis.player.radio.Radio
import com.fidelis.player.radio.Station
import com.fidelis.player.ui.radio.stationColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The home screen widget: the song or station on now, on its own colour, with controls. */
class HomeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RADIO_TOGGLE -> Radio.toggle(context)
            RADIO_STOP -> { Radio.stop(); update(context) }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val RADIO_TOGGLE = "com.fidelis.player.widget.RADIO_TOGGLE"
        private const val RADIO_STOP = "com.fidelis.player.widget.RADIO_STOP"
        private const val S = 240
        private val DEFAULT = Color.rgb(0x5B, 0x3F, 0x8F)

        private data class Song(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean)

        private var song: Song? = null
        private var songArt: Bitmap? = null
        private var songColor = DEFAULT
        private var logoFor: String? = null
        private var logo: Bitmap? = null
        private var logoColor = DEFAULT
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /** From the Spotify bridge. Only redraws when the song, cover or play state changes. */
        fun spotify(context: Context, title: String?, artist: String?, art: Bitmap?, playing: Boolean) {
            val s = if (title.isNullOrBlank()) null else Song(title, artist.orEmpty(), art, playing)
            if (s == song) return
            if (s?.art !== song?.art) {
                songArt = s?.art?.let { runCatching { cover(it, logo = false) }.getOrNull() }
                songColor = s?.art?.let(::tint) ?: DEFAULT
            }
            song = s
            update(context)
        }

        /** Redraws every Fidelis widget on the home screen. The radio wins while a station is on. */
        fun update(context: Context) {
            val ctx = context.applicationContext
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, HomeWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget_now_playing)
            v.setOnClickPendingIntent(R.id.w_root, open(ctx))
            val st = Radio.station
            if (st != null) radioViews(ctx, v, st) else songViews(ctx, v)
            manager.updateAppWidget(ids, v)
        }

        private fun songViews(ctx: Context, v: RemoteViews) {
            val s = song
            v.setViewVisibility(R.id.w_prev, View.VISIBLE)
            if (s == null) {
                v.setTextViewText(R.id.w_eyebrow, "FIDELIS")
                v.setTextViewText(R.id.w_title, "Nothing playing")
                v.setTextViewText(R.id.w_sub, "Tap play to pick up in Spotify")
                v.setImageViewBitmap(R.id.w_art, initials("Fidelis", DEFAULT))
                paint(v, DEFAULT)
            } else {
                v.setTextViewText(R.id.w_eyebrow, if (s.playing) "NOW PLAYING" else "PAUSED")
                v.setTextViewText(R.id.w_title, s.title)
                v.setTextViewText(R.id.w_sub, s.artist)
                v.setImageViewBitmap(R.id.w_art, songArt ?: initials(s.title, songColor))
                paint(v, songColor)
            }
            v.setImageViewResource(R.id.w_play, if (s?.playing == true) R.drawable.widget_pause else R.drawable.widget_play)
            v.setImageViewResource(R.id.w_next, R.drawable.widget_next)
            v.setOnClickPendingIntent(R.id.w_prev, service(ctx, PlaybackService.ACTION_PREV))
            v.setOnClickPendingIntent(R.id.w_play, service(ctx, PlaybackService.ACTION_TOGGLE))
            v.setOnClickPendingIntent(R.id.w_next, service(ctx, PlaybackService.ACTION_NEXT))
        }

        private fun radioViews(ctx: Context, v: RemoteViews, st: Station) {
            if (logoFor != st.id) {
                logoFor = st.id; logo = null
                logoColor = runCatching { stationColor(st.name).toArgb() }.getOrDefault(DEFAULT)
                loadLogo(ctx, st)
            }
            val busy = Radio.playing || Radio.buffering
            v.setTextViewText(R.id.w_eyebrow, if (Radio.buffering) "TUNING IN" else "LIVE RADIO")
            v.setTextViewText(R.id.w_title, st.name)
            v.setTextViewText(R.id.w_sub, Radio.onAir ?: st.meta.ifBlank { "Live radio" })
            v.setImageViewBitmap(R.id.w_art, logo ?: initials(st.name, logoColor))
            paint(v, logoColor)
            v.setViewVisibility(R.id.w_prev, View.GONE)
            v.setImageViewResource(R.id.w_play, if (busy) R.drawable.widget_pause else R.drawable.widget_play)
            v.setImageViewResource(R.id.w_next, R.drawable.widget_stop)
            v.setOnClickPendingIntent(R.id.w_play, broadcast(ctx, RADIO_TOGGLE))
            v.setOnClickPendingIntent(R.id.w_next, broadcast(ctx, RADIO_STOP))
        }

        private fun loadLogo(ctx: Context, st: Station) {
            if (st.favicon.isBlank()) return
            scope.launch {
                val req = ImageRequest.Builder(ctx).data(st.favicon).allowHardware(false).size(S).build()
                val bmp = (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap ?: return@launch
                if (logoFor != st.id) return@launch
                logo = runCatching { cover(bmp, logo = true) }.getOrNull()
                update(ctx)
            }
        }

        /** The background and the play button take the cover's colour. */
        private fun paint(v: RemoteViews, color: Int) {
            v.setInt(R.id.w_bg, "setColorFilter", color)
            v.setInt(R.id.w_play, "setColorFilter", color)
        }

        /** A rich, darkish colour from the cover, so white text always reads. */
        private fun tint(bmp: Bitmap): Int = runCatching {
            val small = Bitmap.createScaledBitmap(soft(bmp), 24, 24, true)
            var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
            val hsv = FloatArray(3)
            for (y in 0 until 24) for (x in 0 until 24) {
                val p = small.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                Color.colorToHSV(p, hsv)
                val k = 0.05 + hsv[1] * hsv[2]
                r += Color.red(p) * k; g += Color.green(p) * k; b += Color.blue(p) * k; w += k
            }
            if (w == 0.0) return DEFAULT
            Color.colorToHSV(Color.rgb((r / w).toInt(), (g / w).toInt(), (b / w).toInt()), hsv)
            if (hsv[1] >= 0.08f) hsv[1] = hsv[1].coerceIn(0.3f, 0.8f)
            hsv[2] = hsv[2].coerceIn(0.3f, 0.5f)
            Color.HSVToColor(hsv)
        }.getOrDefault(DEFAULT)

        private fun soft(b: Bitmap) = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

        /** A rounded square: the cover filling it, or a logo on white like in the app. */
        private fun cover(bmp: Bitmap, logo: Boolean): Bitmap {
            val src = soft(bmp)
            val sq = Bitmap.createBitmap(S, S, Bitmap. Config.ARGB_8888)
            val c = Canvas(sq)
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val k = if (logo) {
                c.drawColor(Color.WHITE)
                minOf(S * 0.76f / src.width, S * 0.76f / src.height)
            } else maxOf(S.toFloat() / src.width, S.toFloat() / src.height)
            val w = src.width * k; val h = src.height * k
            c.drawBitmap(src, null, RectF((S - w) / 2, (S - h) / 2, (S + w) / 2, (S + h) / 2), p)
            return round(sq)
        }

        private fun initials(name: String, color: Int): Bitmap {
            val sq = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
            val c = Canvas(sq)
            val light = ColorUtils.blendARGB(color, Color.WHITE, 0.15f)
            val dark = ColorUtils.blendARGB(color, Color.BLACK, 0.45f)
            c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, S.toFloat(), S.toFloat(), light, dark, Shader.TileMode.CLAMP) })
            val text = name.split(' ').filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2)
                .joinToString("") { it.first().uppercase() }.ifBlank { "♪" }
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; textSize = S * 0.34f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align. CENTER
            }
            c.drawText(text, S / 2f, S / 2f - (tp.descent() + tp.ascent()) / 2, tp)
            return round(sq)
        }

        private fun round(sq: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode. CLAMP) }
            Canvas(out).drawRoundRect(RectF(0f, 0f, S.toFloat(), S.toFloat()), S * 0.16f, S * 0.16f, p)
            return out
        }

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        private fun open(ctx: Context) = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS,
        )

        private fun service(ctx: Context, action: String) = PendingIntent.getForegroundService(
            ctx, action.hashCode(), Intent(ctx, PlaybackService::class.java).setAction(action), FLAGS,
        )

        private fun broadcast(ctx: Context, action: String) = PendingIntent.getBroadcast(
            ctx, action.hashCode(), Intent(ctx, HomeWidget::class.java).setAction(action), FLAGS,
        )
    }
}
