package com.fuelgo.app.domain

import com.fuelgo.app.domain.model.FuelType
import com.fuelgo.app.domain.model.Station
import kotlin.math.roundToInt

/**
 * Pure helpers for ranking, filtering and freshness of the station cache.
 * Kept free of Android/Room dependencies so they are trivially unit-testable.
 */
object StationOps {

    /** How long a cached station POI is considered valid before re-fetching. */
    const val CACHE_TTL_MS: Long = 7 * 24 * 60 * 60 * 1000L

    /**
     * Filters [stations] for [fuelType], ranks by distance from (lat, lon) and
     * returns up to [limit] with their distance in meters attached.
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
            .filter { it.fuelTypes.isEmpty() || fuelType in it.fuelTypes }
            .map { it to Geo.haversineMeters(lat, lon, it.lat, it.lon) }
            .filter { (station, _) -> station.lat != 0.0 || station.lon != 0.0 }
            .sortedBy { it.second }
            .take(limit)
            .toList()

    fun isFresh(fetchedAtMs: Long, nowMs: Long, ttlMs: Long = CACHE_TTL_MS): Boolean =
        fetchedAtMs in (nowMs - ttlMs)..nowMs

    /** Deduplicates stations that both providers may return, preferring Places. */
    fun merge(primary: List<Station>, fallback: List<Station>): List<Station> {
        val seen = mutableSetOf<String>()
        return (primary + fallback).filter { s ->
            // Two stations within ~30 m are the same pump from different sources.
            val key = "${(s.lat * 1000).roundToInt()}/${(s.lon * 1000).roundToInt()}"
            seen.add(key)
        }
    }
}
