package com.fuelgo.app.data.repo

import com.fuelgo.app.data.api.OverpassApi
import com.fuelgo.app.data.api.OverpassQueries
import com.fuelgo.app.data.api.OverpassMirrors
import com.fuelgo.app.data.api.Center
import com.fuelgo.app.data.api.Circle
import com.fuelgo.app.data.api.LocationRestriction
import com.fuelgo.app.data.api.NearbySearchRequest
import com.fuelgo.app.data.db.StationDao
import com.fuelgo.app.data.db.StationEntity
import com.fuelgo.app.domain.StationOps
import com.fuelgo.app.domain.model.FuelType
import com.fuelgo.app.domain.model.Station
import com.fuelgo.app.domain.model.StationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Station data layer. Cache-first: the UI reads [cachedNearby] instantly from
 * Room; [refresh] performs the network fan-out and upserts the result.
 *
 * Provider strategy: when a Google Places key is configured, Places and OSM
 * are fetched in parallel and merged — Places contributes better names and
 * quality opening hours, OSM contributes the fuel-type tags (fuel:diesel /
 * fuel:petrol / fuel:cng) that power the fuel-type filter. Without a key, OSM
 * alone is used. Places results are validated client-side so that POIs not
 * categorized as gas_station are dropped.
 */
class StationRepository(
    private val stationDao: StationDao,
    private val overpassApi: OverpassApi,
    private val placesApi: PlacesApiHandle?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Bundled Places client + key, or null when no key is configured. */
    class PlacesApiHandle(val api: com.fuelgo.app.data.api.PlacesApi, val apiKey: String)

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
     * Network refresh. Returns true when at least one provider responded
     * successfully (even for an empty area); cache untouched on total failure.
     */
    suspend fun refresh(lat: Double, lon: Double, fuelType: FuelType): Boolean =
        withContext(Dispatchers.IO) {
            val now = clock()
            val anySuccess = AtomicBoolean(false)
            var merged: List<Station> = emptyList()

            coroutineScope {
                val osmDeferred = async {
                    fetchOverpassFirst(lat, lon)?.also { anySuccess.set(true) }.orEmpty()
                }
                val placesDeferred: kotlinx.coroutines.Deferred<List<Station>>? =
                    if (placesApi != null) {
                        async {
                            runCatching { fetchPlaces(lat, lon) }
                                .onSuccess { anySuccess.set(true) }
                                .getOrDefault(emptyList())
                        }
                    } else {
                        null
                    }

                val fromOsm = osmDeferred.await()
                val fromPlaces = placesDeferred?.await() ?: emptyList()
                merged = StationOps.merge(fromPlaces, fromOsm)
            }

            if (merged.isEmpty()) return@withContext anySuccess.get()
            stationDao.upsertAll(merged.map { it.toEntity(now) })
            stationDao.pruneOlderThan(now - StationOps.CACHE_TTL_MS)
            anySuccess.get()
        }

    /** Tries each Overpass mirror in turn; null only when every mirror fails. */
    private suspend fun fetchOverpassFirst(lat: Double, lon: Double): List<Station>? {
        for (url in OverpassMirrors.urls) {
            val result = runCatching { fetchOverpass(url, lat, lon) }.getOrNull()
            if (result != null) return result
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
        val handle = placesApi ?: error("Places API not configured")
        val response = handle.api.searchNearby(
            apiKey = handle.apiKey,
            body = NearbySearchRequest(
                locationRestriction = LocationRestriction(
                    circle = Circle(center = Center(lat, lon), radius = 5000.0),
                ),
            ),
        )
        return response.places
            .filter { it.isFuelStation } // drop POIs Google does not categorize as fuel
            .mapNotNull { place ->
                val loc = place.location ?: return@mapNotNull null
                Station(
                    id = "places:${place.id}",
                    name = place.displayName?.text ?: "Fuel station",
                    lat = loc.latitude,
                    lon = loc.longitude,
                    rawOpeningHours = place.regularOpeningHours?.toRawString(),
                    // Places (new API) does not expose per-fuel availability;
                    // empty set = unknown so the fuel filter keeps the station.
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
