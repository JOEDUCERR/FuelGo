package com.fuelgo.app

import com.fuelgo.app.domain.Geo
import com.fuelgo.app.domain.ReportRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRulesTest {

    private val now = 10_000_000L
    private val cooldown = ReportRules.COOLDOWN_MS

    @Test
    fun `first report always allowed`() {
        assertTrue(ReportRules.canReport(null, now))
    }

    @Test
    fun `recent report blocked within cooldown`() {
        assertFalse(ReportRules.canReport(now - 5 * 60 * 1000, now))
        // Exactly at the cooldown edge the report is allowed again (>= window).
        assertTrue(ReportRules.canReport(now - cooldown, now))
    }

    @Test
    fun `old report allowed after cooldown`() {
        assertTrue(ReportRules.canReport(now - cooldown - 1, now))
    }

    @Test
    fun `distance sanity check`() {
        assertTrue(ReportRules.withinRange(ReportRules.MAX_REPORT_DISTANCE_M))
        assertFalse(ReportRules.withinRange(ReportRules.MAX_REPORT_DISTANCE_M + 1))
    }

    @Test
    fun `distance math used by submission is sane`() {
        // Same spot -> closable; 0.01 deg lat away (~1.1 km) -> out of range.
        val near = Geo.haversineMeters(28.61, 77.21, 28.6101, 77.21)
        val far = Geo.haversineMeters(28.61, 77.21, 28.62, 77.21)
        assertTrue(ReportRules.withinRange(near))
        assertFalse(ReportRules.withinRange(far))
    }
}
