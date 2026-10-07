package com.fidelis.player.data.model

/** A codec/quality classification read from the file itself, never guessed from the extension. */
data class Fidelity(
    val codec: String,          // e.g. "FLAC", "ALAC", "MP3", "AAC"
    val bitDepth: Int?,         // null for lossy
    val sampleRateHz: Int,      // e.g. 44100, 96000
    val bitrateKbps: Int?,      // present for lossy
    val isLossless: Boolean,
) {
    val sampleRateKhz: Double get() = sampleRateHz / 1000.0

    /** Short badge text, e.g. "24-bit / 96 kHz" or "320 kbps". */
    fun badge(): String = if (isLossless && bitDepth != null)
        "$bitDepth-bit / ${trimZeros(sampleRateKhz)} kHz"
    else
        "${bitrateKbps ?: "?"} kbps"

    private fun trimZeros(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    companion object {
        val LOSSLESS_CODECS = setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "WAVPACK")
    }
}

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,            // content:// MediaStore uri
    val isrc: String?,          // when present in tags; the matcher's strongest signal
    val fidelity: Fidelity,
    val losslessAvailable: Boolean = false, // true if lossy but a better copy should exist
)
