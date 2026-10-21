package com.fuelgo.app

import com.fuelgo.app.domain.Geo
import com.fuelgo.app.domain.StationOps
import com.fuelgo.app.domain.model.FuelType
import com.fuelgo.app.domain.model.Station
import com.fuelgo.app.domain.model.StationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationOpsTest {

    // Central Delhi for a stable (not India-specific-legal) test point.
    private val lat = 28.6139
    private val lon = 77.2090

    private fun station(
        id: String,
        lat: Double = this.lat,
        lon: Double = this.lon,
        fuels: Set<FuelType> = emptySet(),
    ) = Station(id, "Pump $id", lat, lon, null, fuels, StationSource.OSM)

    @Test
    fun `haversine sanity — one point is zero`() {
        assertEquals(0.0, Geo.haversineMeters(lat, lon, lat, lon), 0.5)
    }

    @Test
    fun `haversine sanity — known distance`() {
        // 0.01 deg of latitude is roughly 1.11 km.
        val d = Geo.haversineMeters(lat, lon, lat + 0.01, lon)
        assertTrue("was $d", d in 1100.0..1125.0)
    }

    @Test
    fun `distance formatting`() {
        assertEquals("850 m", Geo.formatDistance(850.4))
        assertEquals("2.5 km", Geo.formatDistance(2499.0))
    }

    @Test
    fun `ranks by distance and limits results`() {
        val stations = listOf(
            station("far", lat = lat + 0.02),
            station("near", lat = lat + 0.001),
            station("mid", lat = lat + 0.01),
            station("b", lat = lat + 0.003),
        )
        val ranked = StationOps.rankAndFilter(stations, lat, lon, FuelType.DIESEL, limit = 3)
        assertEquals(listOf("near", "b", "mid"), ranked.map { it.first.id })
    }

    @Test
    fun `stations with verified fuel rank above nearer unknown ones`() {
        val stations = listOf(
            station("unknown-nearer", lat = lat + 0.001), // 111 m away, fuel unknown
            station("known-farther", lat = lat + 0.004, fuels = setOf(FuelType.CNG)),
        )
        val ranked = StationOps.rankAndFilter(stations, lat, lon, FuelType.CNG, limit = 5)
        assertEquals(listOf("known-farther", "unknown-nearer"), ranked.map { it.first.id })
    }

    @Test
    fun `filters out stations without requested fuel when tags known`() {
        val stations = listOf(
            station("diesel-only", fuels = setOf(FuelType.DIESEL)),
            station("petrol-only", fuels = setOf(FuelType.PETROL)),
        )
        val ranked = StationOps.rankAndFilter(stations, lat, lon, FuelType.CNG, limit = 5)
        assertEquals(emptyList<String>(), ranked.map { it.first.id })
    }

    @Test
    fun `keeps stations with unknown fuel tags`() {
        val stations = listOf(station("unknown-tags", fuels = emptySet()))
        val ranked = StationOps.rankAndFilter(stations, lat, lon, FuelType.CNG, limit = 5)
        assertEquals(listOf("unknown-tags"), ranked.map { it.first.id })
    }

    @Test
    fun `keeps stations that carry the requested fuel`() {
        val stations = listOf(
            station("has-cng", fuels = setOf(FuelType.CNG, FuelType.DIESEL)),
        )
        val ranked = StationOps.rankAndFilter(stations, lat, lon, FuelType.CNG, limit = 5)
        assertEquals(listOf("has-cng"), ranked.map { it.first.id })
    }

    @Test
    fun `cache freshness window`() {
        val now = 10_000L
        assertTrue(StationOps.isFresh(now - 100, now))
        assertFalse(StationOps.isFresh(now - StationOps.CACHE_TTL_MS - 1, now))
        assertFalse(StationOps.isFresh(now + 1, now))
    }

    @Test
    fun `merge prefers primary sources and dedupes by proximity`() {
        val places = listOf(station("places-1", lat = 28.6, lon = 77.2))
        val osm = listOf(
            station("osm-dup", lat = 28.6, lon = 77.2), // same spot
            station("osm-other", lat = 28.7, lon = 77.3),
        )
        val merged = StationOps.merge(places, osm)
        assertEquals(listOf("places-1", "osm-other"), merged.map { it.id })
    }

    @Test
    fun `merge unions fuel tags from fallback onto primary`() {
        val places = listOf(
            station("places-pump", lat = 28.6, lon = 77.2, fuels = emptySet()),
        )
        val osm = listOf(
            station("osm-pump", lat = 28.6, lon = 77.2, fuels = setOf(FuelType.CNG)),
        )
        val merged = StationOps.merge(places, osm)
        assertEquals(1, merged.size)
        assertEquals("places-pump", merged[0].id)
        assertEquals(setOf(FuelType.CNG), merged[0].fuelTypes)
    }

    @Test
    fun `merge keeps places hours when osm has some too`() {
        val places = listOf(
            Station("places-pump", "Indian Oil", 28.6, 77.2, "0:0800-0:2000", emptySet(), StationSource.PLACES),
        )
        val osm = listOf(
            Station("osm-pump", "Indian Oil", 28.6, 77.2, "Mo-Su 08:00-20:00", emptySet(), StationSource.OSM),
        )
        val merged = StationOps.merge(places, osm)
        assertEquals(1, merged.size)
        assertEquals("0:0800-0:2000", merged[0].rawOpeningHours)
    }
}
