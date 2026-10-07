package com.fidelis.player.data

import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.data.db.TrackEntity
import com.fidelis.player.data.model.Fidelity
import com.fidelis.player.data.model.Track
import com.fidelis.player.data.scan.MediaStoreScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val dao: MusicDao,
) {
    fun tracks(): Flow<List<Track>> =
        dao.observeTracks().map { rows -> rows.map { it.toTrack() } }

    /** Scan MediaStore and upsert into Room. Safe to call repeatedly. */
    suspend fun refresh() {
        val scanned = scanner.scan()
        dao.upsertTracks(scanned)
    }
}

fun TrackEntity.toTrack(): Track = Track(
    id = id, title = title, artist = artist, album = album,
    durationMs = durationMs, uri = uri, isrc = isrc,
    fidelity = Fidelity(codec, bitDepth, sampleRateHz, bitrateKbps, isLossless),
    losslessAvailable = losslessAvailable,
)
