package com.fuelgo.app

import com.fuelgo.app.domain.OpeningHoursParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Week of reference: 2026-10-19 is a Monday.
 * Mon 19, Tue 20, Wed 21, Thu 22, Fri 23, Sat 24, Sun 25 October 2026.
 */
class OpeningHoursParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 10, day), LocalTime.of(hour, minute), zone)

    // Monday = day 19, Saturday = day 24, Sunday = day 25.

    @Test
    fun `24_7 is always open`() {
        val hours = OpeningHoursParser.parse("24/7")!!
        assertTrue(hours.isOpenAt(at(19, 3, 15)))   // Monday 03:15
        assertTrue(hours.isOpenAt(at(25, 23, 59)))  // Sunday 23:59
    }

    @Test
    fun `simple weekday range`() {
        val hours = OpeningHoursParser.parse("Mo-Fr 08:00-20:00")!!
        assertTrue(hours.isOpenAt(at(19, 10, 0)))   // Monday 10:00
        assertTrue(hours.isOpenAt(at(23, 19, 59)))  // Friday 19:59
        assertFalse(hours.isOpenAt(at(19, 7, 59)))  // Monday 07:59
        assertFalse(hours.isOpenAt(at(19, 20, 0)))  // Monday 20:00 (end exclusive)
        assertFalse(hours.isOpenAt(at(24, 10, 0)))  // Saturday
    }

    @Test
    fun `single day rule`() {
        val hours = OpeningHoursParser.parse("Sa 09:00-18:00")!!
        assertTrue(hours.isOpenAt(at(24, 12, 0)))
        assertFalse(hours.isOpenAt(at(19, 12, 0)))
    }

    @Test
    fun `weekday list rule`() {
        val hours = OpeningHoursParser.parse("Mo,We,Fr 09:00-17:00")!!
        assertTrue(hours.isOpenAt(at(21, 10, 0)))   // Wednesday
        assertFalse(hours.isOpenAt(at(22, 10, 0)))  // Thursday
    }

    @Test
    fun `comma separated time ranges`() {
        val hours = OpeningHoursParser.parse("Mo-Fr 08:00-12:00,13:00-17:00")!!
        assertTrue(hours.isOpenAt(at(19, 9, 0)))    // morning
        assertFalse(hours.isOpenAt(at(19, 12, 30))) // lunch gap
        assertTrue(hours.isOpenAt(at(19, 16, 0)))   // afternoon
        assertFalse(hours.isOpenAt(at(19, 17, 0)))  // after afternoon range
    }

    @Test
    fun `overnight range crosses midnight`() {
        val hours = OpeningHoursParser.parse("Sa 22:00-02:00")!!
        assertTrue(hours.isOpenAt(at(24, 23, 0)))   // Saturday 23:00
        assertTrue(hours.isOpenAt(at(25, 1, 0)))    // Sunday 01:00
        assertFalse(hours.isOpenAt(at(24, 21, 0)))  // Saturday 21:00
        assertFalse(hours.isOpenAt(at(25, 2, 0)))   // Sunday 02:00 (end exclusive)
    }

    @Test
    fun `sunday overnight spills into monday`() {
        val hours = OpeningHoursParser.parse("Su 23:00-01:00")!!
        assertTrue(hours.isOpenAt(at(25, 23, 30)))  // Sunday 23:30
        assertTrue(hours.isOpenAt(at(19, 0, 30)))   // Monday 00:30 (next week wrap)
        assertFalse(hours.isOpenAt(at(19, 1, 30)))  // Monday 01:30
    }

    @Test
    fun `multiple rules in one value`() {
        val hours = OpeningHoursParser.parse("Mo-Fr 08:00-20:00; Sa 09:00-13:00")!!
        assertTrue(hours.isOpenAt(at(19, 9, 0)))    // Monday
        assertTrue(hours.isOpenAt(at(24, 10, 0)))   // Saturday
        assertFalse(hours.isOpenAt(at(24, 14, 0)))  // Saturday afternoon
        assertFalse(hours.isOpenAt(at(25, 10, 0)))  // Sunday
    }

    @Test
    fun `off rules are ignored but do not break parsing`() {
        val hours = OpeningHoursParser.parse("Mo-Sa 08:00-20:00; Su off")!!
        assertTrue(hours.isOpenAt(at(24, 10, 0)))   // Saturday
        assertFalse(hours.isOpenAt(at(25, 10, 0)))  // Sunday
    }

    @Test
    fun `midnight end means full day`() {
        val hours = OpeningHoursParser.parse("Mo-Su 00:00-24:00")!!
        assertTrue(hours.isOpenAt(at(22, 0, 0)))
        assertTrue(hours.isOpenAt(at(22, 23, 59)))
    }

    @Test
    fun `unparsable values return null`() {
        assertNull(OpeningHoursParser.parse(null))
        assertNull(OpeningHoursParser.parse(""))
        assertNull(OpeningHoursParser.parse("   "))
        assertNull(OpeningHoursParser.parse("always open"))
        assertNull(OpeningHoursParser.parse("Mo-Fr"))          // no time
        assertNull(OpeningHoursParser.parse("Mo-Fr 8-20"))     // bad time format
        assertNull(OpeningHoursParser.parse("Xx 08:00-20:00")) // bad day
    }

    @Test
    fun `empty rules after off-only value yield null`() {
        assertNull(OpeningHoursParser.parse("Mo off"))
    }

    @Test
    fun `boundary inclusivity — open at start`() {
        val hours = OpeningHoursParser.parse("Mo-Fr 08:00-20:00")!!
        assertTrue(hours.isOpenAt(at(19, 8, 0)))
    }

    @Test
    fun `day range end checks`() {
        val hours = OpeningHoursParser.parse("Mo-Fr 08:00-20:00")!!
        // Friday 20:00 exact end -> closed; Friday covered as last day of range.
        assertEquals(false, hours.isOpenAt(at(23, 20, 0)))
    }
}
