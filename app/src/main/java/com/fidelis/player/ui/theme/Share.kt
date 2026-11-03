package com.fidelis.player.ui.theme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.Telephony
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fidelis.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Apps worth offering, in this order. [image] apps get a story card; the rest get the link. */
private val KNOWN = listOf(
    Triple("com.instagram.android", "Stories", true),
    Triple("com.whatsapp", "WhatsApp", false),
    Triple("org.telegram.messenger", "Telegram", false),
    Triple("com.facebook.orca", "Messenger", false),
    Triple("com.snapchat.android", "Snapchat", true),
    Triple("com.twitter.android", "X", false),
    Triple("com.facebook.katana", "Facebook", false),
)

private class ShareApp(val pkg: String, val label: String, val image: Boolean, val icon: ImageBitmap?)

/** open.spotify.com address for any Spotify uri, so every app shows the song's own preview. */
fun spotifyLink(uri: String): String {
    val parts = uri.split(':')
    return if (parts.size >= 3 && parts[0] == "spotify") "https://open.spotify.com/${parts[parts.size - 2]}/${parts.last()}" else uri
}

/** Spotify-style sharing: a card in the cover's colour, then your apps, Copy link and More. */
@Composable
internal fun ShareContent(title: String, artist: String, uri: String, image: Any?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val link = remember(uri) { spotifyLink(uri) }
    val text = if (artist.isBlank()) "$title\n$link" else "$title · $artist\n$link"
    var cover by remember { mutableStateOf(image as? Bitmap) }
    LaunchedEffect(image) { if (cover == null && image != null) cover = loadBitmap(context, image) }
    val tint = remember(cover) { cover?.let(::artTint) }
    val color by animateColorAsState(tint ?: Fidelis.purple, tween(500), label = "card")
    val apps = remember { installedApps(context) }
    var busy by remember { mutableStateOf(false) }

    fun send(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show() }
        onDone()
    }

    Text("SHARE", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp))
    // The card, as it will look in a story.
    Box(
        Modifier.padding(horizontal = 48.dp).fillMaxWidth().aspectRatio(0.78f)
            .shadow(28.dp, RoundedCornerShape(28.dp), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.08f), lerp(Color.Black, color, 0.4f)))),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f)
                .shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Fidelis.glass)) {
                image?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) Text(artist, color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Text("FIDELIS", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium)
        }
    }
    LazyRow(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Target("Copy link", { Icon(Icons.Rounded.Link, null, tint = Fidelis.ink, modifier = Modifier.size(24.dp)) }) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText(title, link))
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
        items(apps, key = { it.pkg }) { app ->
            Target(app.label, {
                app.icon?.let { Image(it, null, Modifier.fillMaxSize().clip(CircleShape)) }
            }) {
                if (busy) return@Target
                if (!app.image) {
                    send(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(app.pkg))
                } else {
                    busy = true
                    scope.launch {
                        val img = withContext(Dispatchers.Default) { storyImage(context, cover, title, artist, color.toArgb()) }
                        busy = false
                        send(Intent(Intent.ACTION_SEND).setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, img).putExtra(Intent.EXTRA_TEXT, link)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).setPackage(app.pkg)
                            .apply { clipData = ClipData.newRawUri("", img) })
                    }
                }
            }
        }
        item {
            Target("More", { Icon(Icons.Rounded.MoreHoriz, null, tint = Fidelis.ink, modifier = Modifier.size(26.dp)) }) {
                send(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }
        }
    }
}

@Composable
private fun Target(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).pressable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Fidelis.glassStrong), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Fidelis.inkSoft, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The known apps you actually have, with their real icons, plus your SMS app. */
private fun installedApps(context: Context): List<ShareApp> {
    val pm = context.packageManager
    val can = buildSet {
        listOf("text/plain", "image/png").forEach { type ->
            runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType(type), 0) }
                .getOrDefault(emptyList()).forEach { add(it.activityInfo.packageName) }
        }
    }
    fun icon(pkg: String) = runCatching { pm.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap() }.getOrNull()
    val list = KNOWN.filter { it.first in can }.map { (pkg, label, image) -> ShareApp(pkg, label, image, icon(pkg)) }
    val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
    return if (sms != null && sms in can && list.none { it.pkg == sms }) list + ShareApp(sms, "Messages", false, icon(sms)) else list
}

private suspend fun loadBitmap(context: Context, data: Any): Bitmap? = runCatching {
    val r = context.imageLoader.execute(ImageRequest.Builder(context).data(data).allowHardware(false).size(900).build())
    (r as? SuccessResult)?.drawable?.toBitmap()
}.getOrNull()

/** A 1080×1920 story card: the cover on its own colour, title, artist and a quiet Fidelis mark. */
private fun storyImage(context: Context, cover: Bitmap?, title: String, artist: String, color: Int): Uri {
    val w = 1080; val h = 1920
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val top = ColorUtils.blendARGB(color, android.graphics.Color.WHITE, 0.08f)
    val bottom = ColorUtils.blendARGB(android.graphics.Color.BLACK, color, 0.4f)
    c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
    val size = 780f; val left = (w - size) / 2f; val y = 400f
    val rect = RectF(left, y, left + size, y + size)
    c.drawRoundRect(rect, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0x33000000; setShadowLayer(70f, 0f, 34f, 0x99000000.toInt())
    })
    cover?.let { src ->
        val s = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, 44f, 44f, Path.Direction.CW) })
        c.drawBitmap(s, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
    }
    val bold = runCatching { ResourcesCompat.getFont(context, R.font.manrope_extrabold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    val semi = runCatching { ResourcesCompat.getFont(context, R.font.manrope_semibold) }.getOrNull() ?: Typeface.DEFAULT
    fun line(text: String, sizePx: Float, face: Typeface, alpha: Int, at: Float) {
        if (text.isBlank()) return
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx; typeface = face; textAlign = Paint.Align.CENTER
            this.color = android.graphics.Color.argb(alpha, 255, 255, 255)
        }
        c.drawText(TextUtils.ellipsize(text, p, w - 160f, TextUtils.TruncateAt.END).toString(), w / 2f, at, p)
    }
    line(title, 68f, bold, 255, y + size + 150f)
    line(artist, 46f, semi, 215, y + size + 222f)
    line("Listening on Fidelis", 34f, semi, 150, h - 170f)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "story.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "com.fidelis.player.share", f)
}
