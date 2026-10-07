package com.fidelis.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Extracts embedded cover art from local audio files for the notification + session. */
@UnstableApi
class EmbeddedArtBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    @Volatile private var cacheKey: String? = null
    @Volatile private var cacheBitmap: Bitmap? = null

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Could not decode bitmap")
        })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable {
            val key = uri.toString()
            cacheBitmap?.let { if (cacheKey == key) return@Callable it }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val pic = retriever.embeddedPicture ?: error("No embedded picture")
                val bmp = BitmapFactory.decodeByteArray(pic, 0, pic.size) ?: error("Decode failed")
                cacheKey = key
                cacheBitmap = bmp
                bmp
            } finally {
                retriever.release()
            }
        })
}
