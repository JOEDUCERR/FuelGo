package com.fuelgo.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Raw opening-hours string from the provider; parsed on demand. */
    val rawOpeningHours: String?,
    /** Comma-separated FuelType names; empty = unknown. */
    val fuelTypesCsv: String,
    val source: String,
    val fetchedAtMs: Long,
)

@Entity(tableName = "reports", primaryKeys = ["stationId", "atMs"])
data class ReportEntity(
    val stationId: String,
    val atMs: Long,
    /** ReportStatus name. */
    val status: String,
)
