package com.fuelgo.app.data.repo

import com.fuelgo.app.data.api.OverpassApi
import com.fuelgo.app.data.api.OverpassQueries
import com.fuelgo.app.data.api.OverpassMirrors
import com.fuelgo.app.data.api.PlacesApi
import com.fuelgo.app.data.api.Center
import com.fuelgo.app.data.api.Circle
import com.fuelgo.app.data.api.LocationRestriction
import com.fuelgo.app.data.api.NearbySearchRequest
import com.fuelgo.app.data.db.ReportDao
import com.fuelgo.app.data.db.StationDao
import com.fuelgo.app.data.db.StationEntity
import com.fuelgo.app.domain.StationOps
import com.fuelgo.app.domain.model.FuelType
import com.fuelgo.app.domain.model.Station
import com.fuelgo.app.domain.model.StationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Station data layer. Cache-first: the UI reads [cachedNearby] instantly from
 * Room; [refresh] performs at most one network call per invocation and upserts
 * the result.
 *
 * Provider selection: Google Places (new API) when an API key is configured,
 * OSM Overpass otherwise — and as automatic fallback when Places fails.
 */
class StationRepository(
    private val stationDao: StationDao,
    private val overpassApi: OverpassApi,
    private val placesApi: PlacesApi?,
    private val placesApiKey: String?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Instant, offline-capable read: rank cached stations for display. */
    suspend fun cachedNearby(
        lat: Double,
        lon: Double,
        fuelType: FuelType,
        limit: Int = 5,
    ): List<Pair<Station, Double>> = withContext(Dispatchers.IO) {
        val stations = stationDao.all().map { it.toDomain() }
        StationOps.rankAndFilter(stations, lat, lon, fuelType, limit)
    }

    /**
     * Network refresh. Returns true when the network fetch succeeded (either
     * provider); the cache is untouched on failure.
     */
    suspend fun refresh(lat: Double, lon: Double, fuelType: FuelType): Boolean =
        withContext(Dispatchers.IO) {
            val now = clock()
            val fetched = tryFetch(lat, lon)
                ?: return@withContext false
            stationDao.upsertAll(fetched.map { it.toEntity(now) })
            stationDao.pruneOlderThan(now - StationOps.CACHE_TTL_MS)
            true
        }

    private suspend fun tryFetch(lat: Double, lon: Double): List<Station>? {
        if (placesApi != null && !placesApiKey.isNullOrBlank()) {
            val fromPlaces = runCatching { fetchPlaces(lat, lon) }.getOrNull()
            if (fromPlaces != null) return fromPlaces
        }
        // Try each Overpass mirror in turn; the first success wins.
        for (url in OverpassMirrors.urls) {
            val fromOsm = runCatching { fetchOverpass(url, lat, lon) }.getOrNull()
            if (fromOsm != null) return fromOsm
        }
        return null
    }

    private suspend fun fetchOverpass(url: String, lat: Double, lon: Double): List<Station> {
        val response = overpassApi.interpret(url, OverpassQueries.fuelAround(lat, lon))
        return response.elements.mapNotNull { el ->
            val (plat, plon) = when {
                el.lat != null && el.lon != null -> el.lat to el.lon
                el.center != null -> el.center.lat to el.center.lon
                else -> return@mapNotNull null
            }
            val fuelTypes = FuelType.entries.filter { el.tags[it.osmKey] == "yes" }.toSet()
            Station(
                id = "osm:${el.type}/${el.id}",
                name = el.tags["name"] ?: el.tags["operator"] ?: "Fuel station",
                lat = plat,
                lon = plon,
                rawOpeningHours = el.tags["opening_hours"],
                fuelTypes = fuelTypes,
                source = StationSource.OSM,
            )
        }
    }

    private suspend fun fetchPlaces(lat: Double, lon: Double): List<Station> {
        val api = placesApi ?: error("Places API not configured")
        val key = placesApiKey ?: error("Places API key missing")
        val response = api.searchNearby(
            apiKey = key,
            body = NearbySearchRequest(
                locationRestriction = LocationRestriction(
                    circle = Circle(center = Center(lat, lon), radius = 5000.0),
                ),
            ),
        )
        return response.places.mapNotNull { place ->
            val loc = place.location ?: return@mapNotNull null
            Station(
                id = "places:${place.id}",
                name = place.displayName?.text ?: "Fuel station",
                lat = loc.latitude,
                lon = loc.longitude,
                rawOpeningHours = place.regularOpeningHours?.toRawString(),
                // Places (new API) does not expose per-fuel availability; keep
                // empty so no station is filtered out.
                fuelTypes = emptySet(),
                source = StationSource.PLACES,
            )
        }
    }

    private fun Station.toEntity(fetchedAtMs: Long) = StationEntity(
        id = id,
        name = name,
        lat = lat,
        lon = lon,
        rawOpeningHours = rawOpeningHours,
        fuelTypesCsv = fuelTypes.joinToString(",") { it.name },
        source = source.name,
        fetchedAtMs = fetchedAtMs,
    )

    private fun StationEntity.toDomain() = Station(
        id = id,
        name = name,
        lat = lat,
        lon = lon,
        rawOpeningHours = rawOpeningHours,
        fuelTypes = fuelTypesCsv.split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { FuelType.valueOf(it) }.getOrNull() }
            .toSet(),
        source = runCatching { StationSource.valueOf(source) }.getOrDefault(StationSource.OSM),
    )
}

/**
 * Renders Places periods back into the compact raw string format parsed by
 * [com.fuelgo.app.domain.PlacesHoursParser], so the DB stores one uniform
 * hours field. Format: "d:HHMM-d:HHMM" per period, Monday-based day index.
 * A period with no close time is rendered as closing 00:00 the next day.
 */
private fun com.fuelgo.app.data.api.RegularOpeningHoursDto.toRawString(): String =
    periods.mapNotNull { p ->
        val o = p.open ?: return@mapNotNull null
        val openDay = mondayIndex(o.day ?: 0)
        val c = p.close
        if (c != null) {
            "$openDay:${o.time ?: "0000"}-${mondayIndex(c.day ?: 0)}:${c.time ?: "0000"}"
        } else {
            "$openDay:${o.time ?: "0000"}-${(openDay + 1) % 7}:0000"
        }
    }.joinToString(";")

private fun mondayIndex(placesDay: Int) = (placesDay + 6) % 7
