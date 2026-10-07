package com.fidelis.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index("isrc"), Index("normalizedKey")])
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val isrc: String?,
    val codec: String,
    val bitDepth: Int?,
    val sampleRateHz: Int,
    val bitrateKbps: Int?,
    val isLossless: Boolean,
    val losslessAvailable: Boolean,
    /** Normalized "artist|title" used as a cheap match bucket. */
    val normalizedKey: String,
)

/** Links an imported Spotify track to a local file, with the matcher's confidence. */
@Entity(tableName = "spotify_matches", indices = [Index("spotifyId", unique = true)])
data class SpotifyMatchEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val spotifyId: String,
    val spotifyTitle: String,
    val spotifyArtist: String,
    val localTrackId: Long?,     // null = unmatched ("gap")
    val confidence: Int,         // 0..100
    val method: String,          // "ISRC" | "FUZZY"
)

/** On-device audio analysis, computed once at import and cached. */
@Entity(tableName = "audio_features")
data class AudioFeaturesEntity(
    @PrimaryKey val trackId: Long,
    val bpm: Float,
    val camelotKey: String,      // e.g. "8A"
    val energy: Float,           // 0..1
)

/** Queued scrobbles, flushed to Last.fm in batches; survives offline. */
@Entity(tableName = "scrobble_queue")
data class ScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val track: String,
    val album: String?,
    val startedAtEpochSec: Long,
    val sent: Boolean = false,
)
