package com.fuelgo.app

import com.fuelgo.app.data.api.LocationDto
import com.fuelgo.app.data.api.NearbySearchRequest
import com.fuelgo.app.data.api.OverpassApi
import com.fuelgo.app.data.api.OverpassCenter
import com.fuelgo.app.data.api.OverpassElement
import com.fuelgo.app.data.api.OverpassMirrors
import com.fuelgo.app.data.api.OverpassResponse
import com.fuelgo.app.data.api.PlaceDto
import com.fuelgo.app.data.api.PlacesApi
import com.fuelgo.app.data.api.PlacesResponse
import com.fuelgo.app.data.db.StationDao
import com.fuelgo.app.data.db.StationEntity
import com.fuelgo.app.data.repo.StationRepository
import com.fuelgo.app.domain.model.FuelType
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Refresh resilience:
 * - mirror failover on HTTP 406 / hang (per-mirror 6 s budget; first success
 *   wins; per-mirror reasons recorded SEPARATELY),
 * - Places retry exactly once on 5xx, never on 4xx.
 */
class StationRepositoryTest {

    private class FakeDao : StationDao {
        val rows = mutableListOf<StationEntity>()
        override suspend fun all(): List<StationEntity> = rows.toList()
        override suspend fun upsertAll(stations: List<StationEntity>) {
            rows.addAll(stations)
        }
        override suspend fun pruneOlderThan(olderThanMs: Long) {
            rows.removeAll { it.fetchedAtMs < olderThanMs }
        }
        override suspend fun deleteBySource(source: String) {
            rows.removeAll { it.source == source }
        }
        override suspend fun deleteStaleBySource(source: String, keepIds: List<String>) {
            rows.removeAll { it.source == source && it.id !in keepIds }
        }
    }

    /** Behavior keyed by mirror position in [OverpassMirrors.urls]. */
    private class FakeOverpass(val behavior: suspend (mirrorIndex: Int) -> OverpassResponse) : OverpassApi {
        override suspend fun interpret(url: String, query: String): OverpassResponse =
            behavior(OverpassMirrors.urls.indexOf(url))
    }

    private class FakePlaces(val behavior: suspend (attempt: Int) -> PlacesResponse) : PlacesApi {
        var attempts = 0
            private set
        override suspend fun searchNearby(
            apiKey: String,
            fieldMask: String,
            body: NearbySearchRequest,
        ): PlacesResponse {
            attempts++
            return behavior(attempts)
        }
    }

    private fun osmElement(id: Long = 42) = OverpassElement(
        type = "node",
        id = id,
        lat = 30.7046,
        lon = 76.7179,
        center = OverpassCenter(lat = 30.7046, lon = 76.7179),
        tags = mapOf("name" to "IOCL Pump", "fuel:diesel" to "yes", "fuel:petrol" to "yes"),
    )

    private fun validPlace(id: String = "p1") = PlaceDto(
        id = id,
        location = LocationDto(latitude = 30.7046, longitude = 76.7179),
        types = listOf("gas_station"),
        primaryType = "gas_station",
    )

    private fun httpError(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody(null)))

    private fun repo(dao: FakeDao, overpass: OverpassApi, places: PlacesApi?) =
        StationRepository(
            stationDao = dao,
            overpassApi = overpass,
            placesApi = places?.let { StationRepository.PlacesApiHandle(it, apiKey = "test") },
            clock = { 1_000_000L },
            debugLog = { /* silence in unit tests */ },
        )

    // ---- Fix 2: mirror loop ------------------------------------------------

    @Test
    fun `mirror failover - first mirror 406 - second mirror result used, reason recorded per mirror`() = runTest {
        val dao = FakeDao()
        val overpass = FakeOverpass { mirrorIndex ->
            if (mirrorIndex == 0) throw httpError(406, "{\"error\":\"blocked user agent\"}")
            OverpassResponse(elements = listOf(osmElement(42)))
        }
        val repo = repo(dao, overpass, places = null)

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok)
        val ids = dao.rows.map { it.id }
        assertTrue("second mirror's station must be cached, ids=$ids", ids.contains("osm:node/42"))
        val reasons = repo.lastRefreshError.orEmpty()
        assertTrue(
            "first mirror's reason must name it and the status, got: $reasons",
            reasons.contains("overpass-api.de") && reasons.contains("http 406"),
        )
        // Per-mirror separation: nothing recorded for the winning mirror.
        assertFalse(reasons.contains("kumi.systems"))
    }

    @Test
    fun `mirror failover - hung first mirror - virtual 6s per-mirror timeout then next mirror wins`() = runTest {
        val dao = FakeDao()
        val overpass = FakeOverpass { mirrorIndex ->
            if (mirrorIndex == 0) {
                delay(120_000) // would far outlive the real per-mirror budget
                OverpassResponse(elements = emptyList())
            } else {
                OverpassResponse(elements = listOf(osmElement(42)))
            }
        }
        val repository = StationRepository(
            stationDao = dao,
            overpassApi = overpass,
            placesApi = null,
            clock = { 1_000_000L },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            debugLog = { },
        )

        val ok = repository.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok)
        assertTrue(dao.rows.any { it.id == "osm:node/42" })
        val reasons = repository.lastRefreshError.orEmpty()
        assertTrue(
            "hung mirror must record its OWN timeout reason, got: $reasons",
            reasons.contains("overpass-api.de") && reasons.contains("timed out after 6s"),
        )
        assertFalse(reasons.contains("kumi.systems"))
    }

    // ---- Fix 3: Places retry ----------------------------------------------

    @Test
    fun `places retry - 5xx once then success`() = runTest {
        val dao = FakeDao()
        val places = FakePlaces { attempt ->
            if (attempt == 1) throw httpError(500, "{\"error\":{\"message\":\"backend failure\"}}")
            PlacesResponse(places = listOf(validPlace()))
        }
        val repo = repo(dao, FakeOverpass { OverpassResponse(elements = emptyList()) }, places = places)

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok)
        assertEquals(2, places.attempts)
        assertTrue("verified Places result must be cached", dao.rows.any { it.id == "places:p1" })
        assertNull(repo.lastRefreshError)
    }

    @Test
    fun `places retry - 4xx is NOT retried`() = runTest {
        val dao = FakeDao()
        val places = FakePlaces { _ ->
            throw httpError(400, "{\"error\":{\"message\":\"API key not valid\"}}")
        }
        val overpass = FakeOverpass { _ -> throw java.io.IOException("no network") }
        val repo = repo(dao, overpass, places = places)

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertFalse("4xx+dead OSM is a total failure", ok)
        assertEquals("4xx must not be retried", 1, places.attempts)
        val reasons = repo.lastRefreshError.orEmpty()
        assertTrue(reasons.contains("http 400"))
        assertTrue(reasons.contains("API key not valid"))
    }

    // ---- Fix 4 prerequisite: empty results --------------------------------

    @Test
    fun `both providers empty - empty list + honest notice`() = runTest {
        val dao = FakeDao()
        val repo = repo(
            dao,
            FakeOverpass { OverpassResponse(elements = emptyList()) },
            FakePlaces { PlacesResponse(places = emptyList()) },
        )

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok) // providers responded; this is not an offline case
        assertTrue("nothing may be cached when both providers are empty", dao.rows.isEmpty())
        assertEquals("No fuel stations found within 10 km.", repo.lastDataNotice)
    }

    @Test
    fun `all raw Places fail verification and OSM empty - empty list + no unverified rows`() = runTest {
        val dao = FakeDao()
        val repo = repo(
            dao,
            FakeOverpass { OverpassResponse(elements = emptyList()) },
            FakePlaces {
                PlacesResponse(
                    listOf(
                        PlaceDto(id = "tele", types = listOf("office", "gas_station"), primaryType = "office"),
                    ),
                )
            },
        )

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok) // never a blank-screen "offline" case — providers answered
        assertTrue(
            "unverified Places results must not be cached, ids=${dao.rows.map { it.id }}",
            dao.rows.isEmpty(),
        )
        assertEquals("No fuel stations found within 10 km.", repo.lastDataNotice)
    }
}
