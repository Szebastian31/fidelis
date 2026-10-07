package com.fidelis.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // ---- tracks ----
    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks ORDER BY artist, album, title")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE normalizedKey = :key")
    suspend fun tracksByNormalizedKey(key: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isrc = :isrc LIMIT 1")
    suspend fun trackByIsrc(isrc: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: Long): TrackEntity?

    @Query("UPDATE tracks SET losslessAvailable = :available WHERE id = :id")
    suspend fun setLosslessAvailable(id: Long, available: Boolean)

    // ---- matches ----
    @Upsert
    suspend fun upsertMatch(match: SpotifyMatchEntity)

    @Query("SELECT * FROM spotify_matches WHERE localTrackId IS NULL")
    fun observeGaps(): Flow<List<SpotifyMatchEntity>>

    // ---- audio features ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFeatures(features: AudioFeaturesEntity)

    @Query("SELECT * FROM audio_features WHERE trackId = :id")
    suspend fun features(id: Long): AudioFeaturesEntity?

    // ---- scrobbles ----
    @Insert
    suspend fun enqueueScrobble(scrobble: ScrobbleEntity): Long

    @Query("SELECT * FROM scrobble_queue WHERE sent = 0 ORDER BY startedAtEpochSec LIMIT 50")
    suspend fun pendingScrobbles(): List<ScrobbleEntity>

    @Query("UPDATE scrobble_queue SET sent = 1 WHERE id IN (:ids)")
    suspend fun markScrobbled(ids: List<Long>)
}
