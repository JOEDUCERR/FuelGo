package com.fuelgo.app

import com.fuelgo.app.domain.StatusAggregator
import com.fuelgo.app.domain.model.CrowdLevel
import com.fuelgo.app.domain.model.PumpStatus
import com.fuelgo.app.domain.model.Report
import com.fuelgo.app.domain.model.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusAggregatorTest {

    private val now = 1_000_000L
    private val window = StatusAggregator.DEFAULT_WINDOW_MS

    private fun report(status: ReportStatus, ageMs: Long) =
        Report("s1", status, now - ageMs)

    @Test
    fun `no reports falls back to hours status`() {
        val result = StatusAggregator.effective(PumpStatus.CLOSED, emptyList(), now)
        assertEquals(PumpStatus.CLOSED, result.status)
        assertNull(result.crowd)
    }

    @Test
    fun `stale reports are ignored`() {
        val result = StatusAggregator.effective(
            PumpStatus.OPEN,
            listOf(report(ReportStatus.CLOSED, window + 1)),
            now,
        )
        assertEquals(PumpStatus.OPEN, result.status)
        assertNull(result.crowd)
    }

    @Test
    fun `fresh closed report overrides open hours`() {
        val result = StatusAggregator.effective(
            PumpStatus.OPEN,
            listOf(report(ReportStatus.CLOSED, 5 * 60 * 1000)),
            now,
        )
        assertEquals(PumpStatus.CLOSED, result.status)
    }

    @Test
    fun `fresh open report overrides closed hours`() {
        val result = StatusAggregator.effective(
            PumpStatus.CLOSED,
            listOf(report(ReportStatus.OPEN, 5 * 60 * 1000)),
            now,
        )
        assertEquals(PumpStatus.OPEN, result.status)
    }

    @Test
    fun `most recent report wins`() {
        val reports = listOf(
            report(ReportStatus.CLOSED, 30 * 60 * 1000),  // 30 min ago
            report(ReportStatus.QUEUE_SHORT, 5 * 60 * 1000), // 5 min ago, newer
        )
        val result = StatusAggregator.effective(PumpStatus.UNKNOWN, reports, now)
        assertEquals(PumpStatus.OPEN, result.status)
    }

    @Test
    fun `single queue report does not produce a crowd level`() {
        val result = StatusAggregator.effective(
            PumpStatus.OPEN,
            listOf(report(ReportStatus.QUEUE_SHORT, 10 * 60 * 1000)),
            now,
        )
        assertNull(result.crowd)
    }

    @Test
    fun `mostly short queue reports mean quiet`() {
        val reports = (1..3).map { report(ReportStatus.QUEUE_SHORT, 10 * 60 * 1000) } +
            report(ReportStatus.QUEUE_LONG, 12 * 60 * 1000)
        val result = StatusAggregator.effective(PumpStatus.OPEN, reports, now)
        assertEquals(CrowdLevel.QUIET, result.crowd)
    }

    @Test
    fun `balanced queue reports mean moderate`() {
        val reports = listOf(
            report(ReportStatus.QUEUE_SHORT, 5 * 60 * 1000),
            report(ReportStatus.QUEUE_LONG, 7 * 60 * 1000),
        )
        val result = StatusAggregator.effective(PumpStatus.OPEN, reports, now)
        assertEquals(CrowdLevel.MODERATE, result.crowd)
    }

    @Test
    fun `mostly long queue reports mean busy`() {
        val reports = (1..4).map { report(ReportStatus.QUEUE_LONG, 10 * 60 * 1000) }
        val result = StatusAggregator.effective(PumpStatus.OPEN, reports, now)
        assertEquals(CrowdLevel.BUSY, result.crowd)
    }

    @Test
    fun `queue reports imply open even when hours are unknown`() {
        val result = StatusAggregator.effective(
            PumpStatus.UNKNOWN,
            listOf(report(ReportStatus.QUEUE_LONG, 5 * 60 * 1000)),
            now,
        )
        assertEquals(PumpStatus.OPEN, result.status)
    }

    @Test
    fun `report right at window edge counts`() {
        val result = StatusAggregator.effective(
            PumpStatus.OPEN,
            listOf(report(ReportStatus.CLOSED, window)),
            now,
        )
        assertEquals(PumpStatus.CLOSED, result.status)
    }
}
