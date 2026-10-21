package com.fuelgo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StationDao {

    @Query("SELECT * FROM stations")
    suspend fun all(): List<StationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<StationEntity>)

    @Query("DELETE FROM stations WHERE fetchedAtMs < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)

    /** Removes every row of one provider, e.g. all previously cached Places junk. */
    @Query("DELETE FROM stations WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /**
     * Keeps only the given rows of one provider — used after a successful
     * refresh so POIs the provider no longer returns (miscategorized junk
     * Google fixed upstream, out-of-radius entries) don't linger until TTL.
     */
    @Query("DELETE FROM stations WHERE source = :source AND id NOT IN (:keepIds)")
    suspend fun deleteStaleBySource(source: String, keepIds: List<String>)
}

@Dao
interface ReportDao {

    @Query("SELECT * FROM reports WHERE stationId IN (:stationIds) AND atMs >= :sinceMs")
    suspend fun forStationsSince(stationIds: List<String>, sinceMs: Long): List<ReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<ReportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ReportEntity)

    @Query("DELETE FROM reports WHERE atMs < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)
}
