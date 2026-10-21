package com.fuelgo.app

import com.fuelgo.app.data.api.Center
import com.fuelgo.app.data.api.Circle
import com.fuelgo.app.data.api.LocationRestriction
import com.fuelgo.app.data.api.NearbySearchRequest
import com.fuelgo.app.di.AppJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERMANENT regression guard. kotlinx.serialization silently omits
 * default-valued properties from the wire body when `encodeDefaults = false`.
 * That once stripped `includedPrimaryTypes`, `rankPreference` and
 * `maxResultCount` from every Places request we sent (Google answered with
 * "all types nearby" → office/boiler-plant junk). This test serializes the
 * request with THE SAME Json instance AppContainer uses (`AppJson.json`) so
 * production configuration and this test cannot drift apart.
 */
class PlacesRequestRegressionTest {

    @Test
    fun `wire body carries the primary-type filter, rank and count`() {
        val request = NearbySearchRequest(
            locationRestriction = LocationRestriction(
                circle = Circle(
                    center = Center(latitude = 30.7046, longitude = 76.7179),
                    radius = 10_000.0,
                ),
            ),
        )
        val body = AppJson.json.encodeToString(request)
        assertTrue("missing includedPrimaryTypes — request filters silently dropped\nbody: $body", body.contains("\"includedPrimaryTypes\""))
        assertTrue(body.contains("\"gas_station\""))
        assertTrue("missing rankPreference DISTANCE\nbody: $body", body.contains("\"rankPreference\":\"DISTANCE\""))
        assertTrue("missing maxResultCount 20\nbody: $body", body.contains("\"maxResultCount\":20"))
    }

    @Test
    fun `wire body must NOT contain the loose includedTypes filter`() {
        val request = NearbySearchRequest(
            locationRestriction = LocationRestriction(
                circle = Circle(
                    center = Center(latitude = 30.7046, longitude = 76.7179),
                    radius = 10_000.0,
                ),
            ),
        )
        val body = AppJson.json.encodeToString(request)
        assertFalse(
            "includedTypes leaked into the request — it matches stray SECONDARY types " +
                "and re-opens the junk-POI hole",
            body.contains("\"includedTypes\""),
        )
    }
}
