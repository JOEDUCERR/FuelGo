package com.fuelgo.app.domain.model

/** Where the station record came from. */
enum class StationSource { OSM, PLACES }

/**
 * A fuel station, normalized across providers (OSM / Google Places).
 *
 * [rawOpeningHours] is stored as the provider's raw string (OSM `opening_hours`
 * or a Places-derived rendering) and parsed on demand — parsing is cheap and
 * keeps the DB schema simple.
 */
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val rawOpeningHours: String?,
    /** Known fuel types at this station; empty means unknown (do not filter out). */
    val fuelTypes: Set<FuelType>,
    val source: StationSource,
)
