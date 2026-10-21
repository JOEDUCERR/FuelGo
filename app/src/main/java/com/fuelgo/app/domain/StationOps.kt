package com.fuelgo.app.domain

import com.fuelgo.app.domain.model.FuelType
import com.fuelgo.app.domain.model.Station
import com.fuelgo.app.domain.model.StationSource
import kotlin.math.roundToInt

/**
 * Pure helpers for ranking, filtering and freshness of the station cache.
 * Kept free of Android/Room dependencies so they are trivially unit-testable.
 */
object StationOps {

    /** How long a cached station POI is considered valid before re-fetching. */
    const val CACHE_TTL_MS: Long = 7 * 24 * 60 * 60 * 1000L

    /**
     * Filters [stations] for [fuelType] and ranks for display: stations KNOWN
     * to carry the requested fuel type always rank above stations whose fuel
     * availability is unknown, then by distance from (lat, lon). Returns up to
     * [limit] with their distance in meters attached.
     *
     * Stations with no known fuel tags are kept (unknown != absent).
     */
    fun rankAndFilter(
        stations: List<Station>,
        lat: Double,
        lon: Double,
        fuelType: FuelType,
        limit: Int = 5,
    ): List<Pair<Station, Double>> =
        stations
            .asSequence()
            .map { it to Geo.haversineMeters(lat, lon, it.lat, it.lon) }
            .filter { (station, _) -> station.lat != 0.0 || station.lon != 0.0 }
            .filter { (station, _) -> station.fuelTypes.isEmpty() || fuelType in station.fuelTypes }
            .sortedWith(
                compareBy(
                    { if (fuelType in it.first.fuelTypes) 0 else 1 }, // verified availability first
                    { it.second },                                    // then distance
                ),
            )
            .take(limit)
            .toList()

    fun isFresh(fetchedAtMs: Long, nowMs: Long, ttlMs: Long = CACHE_TTL_MS): Boolean =
        fetchedAtMs in (nowMs - ttlMs)..nowMs

    /**
     * Deduplicates stations both providers may return, collapsing records that
     * map to ~the same spot: Places name/hours survive, and fuel tags from all
     * sources are unioned (OSM carries the diesel/petrol/CNG knowledge).
     */
    fun merge(primary: List<Station>, fallback: List<Station>): List<Station> {
        val cells = LinkedHashMap<String, MutableList<Station>>()
        (primary + fallback).forEach { s ->
            cells.getOrPut(cellKey(s)) { mutableListOf() }.add(s)
        }
        return cells.values.map { group ->
            if (group.size == 1) group.first() else group.reduce { a, b -> combine(a, b) }
        }
    }

    private fun cellKey(s: Station): String =
        "${(s.lat * 1000).roundToInt()}/${(s.lon * 1000).roundToInt()}"

    /** Merge two records of ~the same pump: Places identity + unioned fuel tags. */
    private fun combine(a: Station, b: Station): Station {
        val (first, second) = when (StationSource.PLACES) {
            a.source -> a to b
            b.source -> b to a
            else -> a to b
        }
        return first.copy(
            fuelTypes = (first.fuelTypes + second.fuelTypes),
            rawOpeningHours = first.rawOpeningHours ?: second.rawOpeningHours,
        )
    }
}
