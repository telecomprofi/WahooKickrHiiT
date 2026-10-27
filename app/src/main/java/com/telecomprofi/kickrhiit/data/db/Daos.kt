package com.telecomprofi.kickrhiit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE missing = 0 ORDER BY playlistOrder ASC")
    fun all(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE missing = 0 ORDER BY playlistOrder ASC")
    suspend fun allOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE bpm <= 0 AND analyzedAt = 0 AND missing = 0 ORDER BY playlistOrder ASC")
    suspend fun unanalysed(): List<TrackEntity>

    /** Anything missing either measurement; one decode produces both. */
    @Query(
        "SELECT * FROM tracks WHERE missing = 0 AND " +
            "((bpm <= 0 AND analyzedAt = 0) OR loudnessLufs = 0) ORDER BY playlistOrder ASC"
    )
    suspend fun pendingAnalysis(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE missing = 0")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE bpm > 0 AND missing = 0")
    fun analysedCount(): Flow<Int>

    @Query("UPDATE tracks SET bpm = :bpm, bpmConfidence = :conf, analyzedAt = :at WHERE uri = :uri")
    suspend fun setBpm(uri: String, bpm: Double, conf: Double, at: Long)

    @Query(
        "UPDATE tracks SET bpm = :bpm, bpmConfidence = :conf, loudnessLufs = :lufs, " +
            "bpmSource = :source, analyzedAt = :at WHERE uri = :uri"
    )
    suspend fun setAnalysis(
        uri: String,
        bpm: Double,
        conf: Double,
        lufs: Double,
        at: Long,
        source: Int = BpmSource.MEASURED,
    )

    /** A tempo typed in by hand, which outranks anything measured or tagged. */
    @Query("UPDATE tracks SET bpm = :bpm, bpmConfidence = 1.0, bpmSource = :source WHERE uri = :uri")
    suspend fun setManualBpm(uri: String, bpm: Double, source: Int = BpmSource.MANUAL)

    @Query("UPDATE tracks SET bpm = 0, bpmConfidence = 0, loudnessLufs = 0, bpmSource = 0, analyzedAt = 0")
    suspend fun clearAnalysis()

    @Query("UPDATE tracks SET missing = :missing WHERE uri = :uri")
    suspend fun setMissing(uri: String, missing: Boolean)

    @Query("SELECT COALESCE(MAX(playlistOrder), -1) FROM tracks")
    suspend fun maxOrder(): Int

    @Query("DELETE FROM tracks WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM tracks")
    suspend fun clear()
}

@Dao
interface CatalogueDao {

    @Upsert
    suspend fun upsert(rows: List<CatalogueEntity>)

    @Query("SELECT COUNT(*) FROM catalogue")
    fun count(): Flow<Int>

    @Query(
        "SELECT * FROM catalogue WHERE :q = '' OR title LIKE '%' || :q || '%' OR " +
            "artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' OR " +
            "fileName LIKE '%' || :q || '%' ORDER BY artist, album, fileName LIMIT 200"
    )
    suspend fun search(q: String): List<CatalogueEntity>

    @Query("SELECT * FROM catalogue WHERE uri IN (:uris)")
    suspend fun byUris(uris: List<String>): List<CatalogueEntity>

    @Query("DELETE FROM catalogue")
    suspend fun clear()
}

@Dao
interface RideDao {

    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Query("SELECT * FROM rides ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int = 30): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int = 30): List<RideEntity>

    @Query("SELECT * FROM rides WHERE startedAt >= :from ORDER BY startedAt ASC")
    fun since(from: Long): Flow<List<RideEntity>>

    @Query("SELECT COALESCE(MAX(bestSprintW), 0) FROM rides WHERE workoutId = :workoutId")
    suspend fun bestSprintFor(workoutId: String): Int

    @Query("SELECT COUNT(*) FROM rides")
    fun total(): Flow<Int>

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: Long)
}
