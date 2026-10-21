package com.fuelgo.app.data.repo

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Station data layer. Cache-first: the UI reads [cachedNearby] instantly from
 * Room; [refresh] performs the network fan-out and upserts the result.
 *
 * Provider strategy: when a Google Places key is configured, Places and OSM
 * are fetched in parallel and merged — Places contributes better names and
 * quality opening hours, OSM contributes the fuel-type tags (fuel:diesel /
 * fuel:petrol / fuel:cng) that power the fuel-type filter. Without a key, OSM
 * alone is used.
 *
 * The client NEVER re-validates Places results by category: the request
 * already filters server-side by gas_station, and a client-side re-filter can
 * only shrink the list toward zero when Google's categorization is imperfect —
 * which emptied station lists in the field. Zero-result refreshes are surfaced
 * via [lastRefreshError] so the UI can tell "offline" apart from "network OK,
 * no data".
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
     * Failure reasons are collected into [lastRefreshError] for diagnosability.
     */
    suspend fun refresh(lat: Double, lon: Double, fuelType: FuelType): Boolean =
        withContext(Dispatchers.IO) {
            val now = clock()
            val anySuccess = AtomicBoolean(false)
            val errors = ConcurrentLinkedQueue<String>()
            var merged: List<Station> = emptyList()

            coroutineScope {
                val osmDeferred: Deferred<List<Station>> = async {
                    val result = withTimeoutOrNull(OSM_TOTAL_TIMEOUT_MS) {
                        fetchOverpassFirst(lat, lon) { mirror, msg -> errors.add("osm: $mirror — $msg") }
                    }
                    if (result == null) {
                        errors.add("osm: no mirror answered within ${OSM_TOTAL_TIMEOUT_MS / 1000}s")
                        emptyList()
                    } else {
                        result.also { anySuccess.set(true) }
                    }
                }
                val placesDeferred: Deferred<List<Station>>? = if (placesApi != null) {
                    async {
                        safeFetch(Errors.PLACES_TAG, errors) { fetchPlaces(lat, lon) }
                            .also { if (it != null) anySuccess.set(true) }
                            .orEmpty()
                    }
                } else {
                    null
                }

                val fromOsm = osmDeferred.await()
                val fromPlaces = placesDeferred?.await() ?: emptyList()
                merged = StationOps.merge(fromPlaces, fromOsm)
                Log.d(
                    TAG,
                    "refresh(lat=$lat, lon=$lon, fuel=$fuelType): " +
                        "places=${fromPlaces.size}, osm=${fromOsm.size}, merged=${merged.size}, " +
                        "ok=${anySuccess.get()}, errors=[${errors.joinToString("; ")}]",
                )
            }

            lastRefreshError = errors.joinToString("; ").ifBlank { null }
            if (merged.isEmpty()) {
                if (anySuccess.get()) {
                    // Providers reachable but zero data — NOT an offline case.
                    // Record it so the UI can say this explicitly instead of
                    // spinning forever or blaming the connection.
                    lastRefreshError = "network OK, but 0 stations merged within " +
                        "${OverpassQueries.RADIUS_M / 1000} km"
                    return@withContext true
                }
                return@withContext false
            }
            stationDao.upsertAll(merged.map { it.toEntity(now) })
            stationDao.pruneOlderThan(now - StationOps.CACHE_TTL_MS)
            anySuccess.get()
        }

    /**
     * Why the last [refresh] call had problems, human-readable per provider.
     * Null when the last refresh was fully clean.
     */
    @Volatile
    var lastRefreshError: String? = null
        private set

    /** Number of places returned by Google before the fuel-station filter. */
    @Volatile
    private var rawPlacesResultCount = 0

    /** Total wall-clock budget for the whole Overpass mirror loop. */
    private val OSM_TOTAL_TIMEOUT_MS = 20_000L

    /** Search radius for both providers (Places circle + Overpass query). */
    private val SEARCH_RADIUS_M = OverpassQueries.RADIUS_M.toDouble()

    private companion object {
        const val TAG = "FuelGo"
    }

    /** Failure-reason tag prefixes for the two providers. */
    private object Errors {
        const val PLACES_TAG = "places"
        const val OSM_TAG = "osm"
    }

    /**
     * Cancellation-safe replacement for `runCatching` on suspend calls:
     * sleeps/errors are recorded, but [CancellationException] is always
     * rethrown so coroutine cancellation and our timeouts keep working.
     */
    private suspend fun <T> safeFetch(tag: String, errors: ConcurrentLinkedQueue<String>, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("$tag: ${e.message ?: e.javaClass.simpleName}")
            null
        }

    /** Tries each Overpass mirror in turn; null only when every mirror fails. */
    private suspend fun fetchOverpassFirst(
        lat: Double,
        lon: Double,
        onMirrorError: (mirror: String, message: String) -> Unit,
    ): List<Station>? {
        for (url in OverpassMirrors.urls) {
            val mirror = url.substringBefore("/api/")
                .removePrefix("https://").removePrefix("http://")
            val result = try {
                fetchOverpass(url, lat, lon)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMirrorError(mirror, e.message ?: e.javaClass.simpleName)
                null
            }
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
                    circle = Circle(center = Center(lat, lon), radius = SEARCH_RADIUS_M.toDouble()),
                ),
            ),
        )
        rawPlacesResultCount = response.places.size
        return response.places
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
