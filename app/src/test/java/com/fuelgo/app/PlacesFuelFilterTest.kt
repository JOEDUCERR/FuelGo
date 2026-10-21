package com.fuelgo.app

import com.fuelgo.app.data.api.PlaceDto
import com.fuelgo.app.data.api.PlacesFuelFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Places verification is PRIMARY-TYPE-ONLY: a POI counts as a petrol pump
 * exclusively when Google's primary category is `gas_station`. A secondary
 * `gas_station` in types[] must NOT pass (that hole produced field junk like
 * "Cheema Boilers Limited", "Teleperformance Mohali").
 */
class PlacesFuelFilterTest {

    private fun place(
        id: String = "p",
        types: List<String> = emptyList(),
        primaryType: String? = null,
    ) = PlaceDto(id = id, types = types, primaryType = primaryType)

    @Test
    fun `real petrol pump - primary type gas_station - passes`() {
        val pump = place(
            id = "ioc",
            types = listOf("gas_station", "point_of_interest", "establishment"),
            primaryType = "gas_station",
        )
        assertTrue(pump.isCategorizedFuelPump)
        assertEquals(listOf(pump), PlacesFuelFilter.select(listOf(pump)))
    }

    @Test
    fun `secondary gas_station type alone must NOT pass the filter`() {
        val junk = place(id = "tele", types = listOf("office", "gas_station"), primaryType = "office")
        assertFalse(junk.isCategorizedFuelPump)
        assertTrue(PlacesFuelFilter.select(listOf(junk)).isEmpty())
    }

    @Test
    fun `junk businesses fail the filter`() {
        // Field-reported junk near Mohali.
        val cheema = place(id = "cheema", types = listOf("corporate_office", "point_of_interest"), primaryType = "corporate_office")
        val tele = place(id = "tele", types = listOf("office", "point_of_interest"), primaryType = "office")
        val boiler = place(id = "boiler", types = listOf("establishment"), primaryType = "establishment")
        assertFalse(cheema.isCategorizedFuelPump)
        assertFalse(tele.isCategorizedFuelPump)
        assertFalse(boiler.isCategorizedFuelPump)
    }

    @Test
    fun `absence of type info is NOT verification`() {
        assertFalse(place().isCategorizedFuelPump)
    }

    @Test
    fun `select keeps only primaryTyped gas_station from a mixed result set`() {
        val raw = listOf(
            place(id = "cheema", types = listOf("corporate_office", "point_of_interest"), primaryType = "corporate_office"),
            place(id = "ioc", types = listOf("gas_station", "point_of_interest"), primaryType = "gas_station"),
            place(id = "tele", types = listOf("office", "gas_station"), primaryType = "office"),
        )
        val selected = PlacesFuelFilter.select(raw)
        assertEquals(listOf("ioc"), selected.map { it.id })
    }

    @Test
    fun `verification emptying the list STAYS empty - raw results are never shown`() {
        val raw = listOf(
            place(id = "cheema", types = listOf("corporate_office"), primaryType = "corporate_office"),
            place(id = "typeless"),
        )
        assertTrue(PlacesFuelFilter.select(raw).isEmpty())
    }

    @Test
    fun `empty raw stays empty`() {
        assertTrue(PlacesFuelFilter.select(emptyList()).isEmpty())
    }
}
