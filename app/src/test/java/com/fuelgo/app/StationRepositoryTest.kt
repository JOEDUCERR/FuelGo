package com.fuelgo.app

import com.fuelgo.app.data.api.PlaceDto
import com.fuelgo.app.data.api.NearbySearchRequest
import com.fuelgo.app.data.api.OverpassApi
import com.fuelgo.app.data.api.OverpassCenter
import com.fuelgo.app.data.api.OverpassElement
import com.fuelgo.app.data.api.OverpassResponse
import com.fuelgo.app.data.api.PlacesApi
import com.fuelgo.app.data.api.PlacesResponse
import com.fuelgo.app.data.db.StationDao
import com.fuelgo.app.data.db.StationEntity
import com.fuelgo.app.data.repo.StationRepository
import com.fuelgo.app.domain.model.FuelType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empty-result behavior of [StationRepository.refresh]:
 * - verified Places results empty + OSM non-empty -> OSM rows are shown,
 *   NO Places rows are shown (unverified Places POIs never surface);
 * - both empty -> empty list + the honest [StationRepository.lastDataNotice].
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

    private class FakeOverpass(val elements: List<OverpassElement>) : OverpassApi {
        override suspend fun interpret(url: String, query: String): OverpassResponse =
            OverpassResponse(elements = elements)
    }

    private class FakePlaces(val places: List<PlaceDto>) : PlacesApi {
        override suspend fun searchNearby(
            apiKey: String,
            fieldMask: String,
            body: NearbySearchRequest,
        ): PlacesResponse = PlacesResponse(places = places)
    }

    /** Office-type junk — everywhere in Google's data near Mohali. */
    private val junkPlaces = listOf(
        PlaceDto(id = "tele", types = listOf("office", "gas_station"), primaryType = "office"),
        PlaceDto(id = "cheema", types = listOf("corporate_office", "point_of_interest"), primaryType = "corporate_office"),
    )

    private fun repo(dao: FakeDao, overpass: List<OverpassElement>, places: List<PlaceDto>) =
        StationRepository(
            stationDao = dao,
            overpassApi = FakeOverpass(overpass),
            placesApi = StationRepository.PlacesApiHandle(FakePlaces(places), apiKey = "test"),
            clock = { 1_000_000L },
            debugLog = { /* silence in unit tests */ },
        )

    private fun osmStation(id: Long = 7) = OverpassElement(
        type = "node",
        id = id,
        lat = 30.7046,
        lon = 76.7179,
        center = OverpassCenter(lat = 30.7046, lon = 76.7179),
        tags = mapOf("name" to "IOCL Pump", "fuel:diesel" to "yes", "fuel:petrol" to "yes"),
    )

    @Test
    fun `verified Places empty + OSM non-empty - OSM shown, no Places rows`() = runTest {
        val dao = FakeDao()
        val repo = repo(dao, overpass = listOf(osmStation(7)), places = junkPlaces)

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok)
        val ids = dao.rows.map { it.id }
        assertTrue("OSM row must be cached, ids=$ids", ids.any { it == "osm:node/7" })
        assertFalse(
            "unverified Places rows must never surface, ids=$ids",
            ids.any { it.startsWith("places:") },
        )
        assertNull(repo.lastDataNotice)
    }

    @Test
    fun `both providers empty - empty list + honest notice`() = runTest {
        val dao = FakeDao()
        val repo = repo(dao, overpass = emptyList(), places = emptyList())

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok) // providers responded; this is not an offline case
        assertTrue("nothing may be cached when both providers are empty", dao.rows.isEmpty())
        assertEquals("No fuel stations found within 10 km.", repo.lastDataNotice)
    }

    @Test
    fun `all raw Places fail verification and OSM empty - empty list + notice`() = runTest {
        val dao = FakeDao()
        val repo = repo(dao, overpass = emptyList(), places = junkPlaces)

        val ok = repo.refresh(lat = 30.7046, lon = 76.7179, fuelType = FuelType.DIESEL)

        assertTrue(ok) // never a blank-screen "offline" case — providers answered
        assertTrue(
            "unverified Places results must not be cached, ids=${dao.rows.map { it.id }}",
            dao.rows.isEmpty(),
        )
        assertEquals("No fuel stations found within 10 km.", repo.lastDataNotice)
    }
}
