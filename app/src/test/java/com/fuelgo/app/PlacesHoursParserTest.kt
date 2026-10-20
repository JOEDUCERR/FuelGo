package com.fuelgo.app

import com.fuelgo.app.domain.PlacesHoursParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlacesHoursParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 10, day), LocalTime.of(hour, minute), zone)

    // Reference week: Mon 19 Oct 2026 ... Sun 25 Oct 2026.

    @Test
    fun `simple daily range`() {
        // The raw format carries an explicit Monday-based day index: day 0 = Monday.
        val hours = PlacesHoursParser.parse("0:0800-0:2000")!!
        assertTrue(hours.isOpenAt(at(19, 12, 0)))   // Monday 12:00
        assertFalse(hours.isOpenAt(at(19, 21, 0)))  // Monday 21:00
        assertFalse(hours.isOpenAt(at(25, 12, 0)))  // Sunday not covered by day 0
    }

    @Test
    fun `no close time means open until midnight next day`() {
        // Sunday open 00:00, no close -> rendered as closing Monday 00:00.
        val hours = PlacesHoursParser.parse("6:0000-0:0000")!!
        assertTrue(hours.isOpenAt(at(25, 12, 0)))   // Sunday noon
        assertTrue(hours.isOpenAt(at(25, 23, 59)))  // Sunday 23:59
        assertFalse(hours.isOpenAt(at(19, 1, 0)))   // Monday 01:00
    }

    @Test
    fun `multi period value`() {
        val hours = PlacesHoursParser.parse("0:0800-0:1300;1:0800-1:1300")!!
        assertTrue(hours.isOpenAt(at(19, 10, 0)))   // Monday 10:00
        assertTrue(hours.isOpenAt(at(20, 10, 0)))   // Tuesday 10:00
        assertFalse(hours.isOpenAt(at(21, 10, 0)))  // Wednesday 10:00
    }

    @Test
    fun `unparsable values return null`() {
        assertNull(PlacesHoursParser.parse(null))
        assertNull(PlacesHoursParser.parse(""))
        assertNull(PlacesHoursParser.parse("garbage"))
        assertNull(PlacesHoursParser.parse("8:0800-8:2000"))  // day out of range
        assertNull(PlacesHoursParser.parse("0:08-0:20"))       // bad time
    }
}
