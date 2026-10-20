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
