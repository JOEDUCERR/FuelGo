package com.fuelgo.app

import com.fuelgo.app.data.api.PlaceDto
import com.fuelgo.app.data.api.PlacesFuelFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Codifies the researched semantics of how Google Places categorizes Indian
 * petrol pumps, and the never-blank guarantee of [PlacesFuelFilter]:
 * junk is dropped when Google categorizes it otherwise, but a categorization
 * quirk can never empty the station list.
 */
class PlacesFuelFilterTest {

    private fun place(
        id: String = "p",
        types: List<String> = emptyList(),
        primaryType: String? = null,
    ) = PlaceDto(id = id, types = types, primaryType = primaryType)

    // --- positive-evidence property ----------------------------------------

    @Test
    fun `real petrol pump - primary type gas_station - is categorized`() {
        assertTrue(place(id = "ioc", primaryType = "gas_station", types = listOf("gas_station", "point_of_interest")).isCategorizedFuelPump)
    }

    @Test
    fun `pump typed gas_station only as secondary - positively categorized`() {
        assertTrue(place(types = listOf("store", "gas_station")).isCategorizedFuelPump)
    }

    @Test
    fun `junk POIs are not positively categorized`() {
        // Field-reported junk: miscategorized businesses near Mohali.
        assertFalse(place(id = "cheema", primaryType = "corporate_office", types = listOf("corporate_office", "point_of_interest")).isCategorizedFuelPump)
        assertFalse(place(id = "teleperformance", primaryType = "office", types = listOf("office", "point_of_interest")).isCategorizedFuelPump)
    }

    @Test
    fun `absence of type info is NOT positive evidence`() {
        assertFalse(place().isCategorizedFuelPump)
    }

    // --- filter behavior ----------------------------------------------------

    @Test
    fun `junk filtered out, real pumps kept`() {
        val raw = listOf(
            place(id = "cheema", primaryType = "corporate_office", types = listOf("corporate_office", "point_of_interest")),
            place(id = "ioc", primaryType = "gas_station", types = listOf("gas_station", "point_of_interest")),
            place(id = "teleperformance", primaryType = "office", types = listOf("office", "point_of_interest")),
        )
        val selected = PlacesFuelFilter.select(raw)
        assertEquals(listOf("ioc"), selected.map { it.id })
    }

    @Test
    fun `verification emptying the list falls back to raw results - never blank`() {
        val raw = listOf(
            place(id = "cheema", primaryType = "corporate_office", types = listOf("corporate_office", "point_of_interest")),
            place(id = "typeless"),
        )
        val selected = PlacesFuelFilter.select(raw)
        assertEquals(raw, selected)
    }

    @Test
    fun `empty raw stays empty`() {
        assertTrue(PlacesFuelFilter.select(emptyList()).isEmpty())
    }
}
