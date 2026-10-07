package com.fidelis.player.data.scan

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.provider.MediaStore
import com.fidelis.player.data.db.TrackEntity
import com.fidelis.player.data.model.Fidelity
import com.fidelis.player.util.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans MediaStore for audio and, for each file, reads the REAL format off the stream
 * via MediaExtractor so fidelity badges reflect the bytes, not the file extension.
 */
class MediaStoreScanner(private val context: Context) {

    suspend fun scan(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val out = ArrayList<TrackEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString()).build()
                val fidelity = readFidelity(uri.toString()) ?: continue
                val title = c.getString(titleCol) ?: "Unknown"
                val artist = c.getString(artistCol) ?: "Unknown"
                out += TrackEntity(
                    id = id, title = title, artist = artist,
                    album = c.getString(albumCol) ?: "", durationMs = c.getLong(durCol),
                    uri = uri.toString(),
                    isrc = null, // populate via JAudiotagger pass (Vorbis comment ISRC)
                    codec = fidelity.codec, bitDepth = fidelity.bitDepth,
                    sampleRateHz = fidelity.sampleRateHz, bitrateKbps = fidelity.bitrateKbps,
                    isLossless = fidelity.isLossless, losslessAvailable = false,
                    normalizedKey = Normalizer.key(artist, title),
                )
            }
        }
        out
    }

    private fun readFidelity(uri: String): Fidelity? {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, android.net.Uri.parse(uri), null)
            if (ex.trackCount == 0) return null
            val f = ex.getTrackFormat(0)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = codecName(mime)
            val sampleRate = f.getIntOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val bitDepth = f.getIntOrNull("bits-per-sample") // present on many lossless streams
            val bitrate = f.getIntOrNull(MediaFormat.KEY_BIT_RATE)?.div(1000)
            val lossless = codec in Fidelity.LOSSLESS_CODECS
            Fidelity(
                codec = codec,
                bitDepth = if (lossless) (bitDepth ?: 16) else null,
                sampleRateHz = sampleRate,
                bitrateKbps = if (lossless) null else bitrate,
                isLossless = lossless,
            )
        } catch (e: Exception) {
            null
        } finally {
            ex.release()
        }
    }

    private fun codecName(mime: String): String = when {
        mime.contains("flac") -> "FLAC"
        mime.contains("alac") -> "ALAC"
        mime.contains("raw") || mime.contains("wav") -> "WAV"
        mime.contains("mpeg") -> "MP3"
        mime.contains("aac") || mime.contains("mp4a") -> "AAC"
        mime.contains("vorbis") -> "VORBIS"
        mime.contains("opus") -> "OPUS"
        else -> mime.substringAfter('/').uppercase()
    }

    private fun MediaFormat.getIntOr(key: String, default: Int) =
        if (containsKey(key)) getInteger(key) else default

    private fun MediaFormat.getIntOrNull(key: String) =
        if (containsKey(key)) getInteger(key) else null
}
