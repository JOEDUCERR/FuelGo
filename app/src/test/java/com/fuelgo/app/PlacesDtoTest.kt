package com.fuelgo.app

import com.fuelgo.app.data.api.PlaceDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focus of these tests: the permissive-safe fuel-station categorization rule —
 * a data quirk in Google's categorization must never empty the station list.
 */
class PlacesDtoTest {

    private fun place(types: List<String> = emptyList(), primaryType: String? = null) =
        PlaceDto(id = "p", types = types, primaryType = primaryType)

    @Test
    fun `explicitly categorized gas stations are kept`() {
        assertTrue(place(types = listOf("gas_station", "point_of_interest")).isFuelStation)
        assertTrue(place(primaryType = "gas_station").isFuelStation)
    }

    @Test
    fun `non-fuel categorizations are dropped`() {
        // Cheema-Boilers-style junk: present but contradicting types.
        assertFalse(place(types = listOf("manufacturer", "point_of_interest")).isFuelStation)
        assertFalse(place(primaryType = "store", types = listOf("store")).isFuelStation)
        assertFalse(place(primaryType = "store").isFuelStation)
    }

    @Test
    fun `missing categorization is kept`() {
        // Unknown != absent: never let a type quirk empty the list.
        assertTrue(place().isFuelStation)
        assertTrue(place(types = emptyList(), primaryType = null).isFuelStation)
    }
}
