package com.fuelgo.app.domain

import com.fuelgo.app.domain.model.CrowdLevel
import com.fuelgo.app.domain.model.EffectiveStatus
import com.fuelgo.app.domain.model.PumpStatus
import com.fuelgo.app.domain.model.Report
import com.fuelgo.app.domain.model.ReportStatus

/**
 * Combines hours-derived status with fresh crowdsourced reports.
 *
 * Rules (deliberately simple and deterministic):
 *  - Only reports within [windowMs] of [nowMs] count.
 *  - With no fresh reports, the hours-derived status stands and crowd is null.
 *  - With fresh reports, the MOST RECENT report wins: CLOSED -> CLOSED,
 *    OPEN/QUEUE_* -> OPEN (a queue implies the pump is dispensing).
 *  - Crowd level comes from the ratio of fresh queue reports and needs at least
 *    [MIN_QUEUE_REPORTS] of them; three buckets only, no invented precision.
 */
object StatusAggregator {

    const val DEFAULT_WINDOW_MS: Long = 60 * 60 * 1000L
    const val MIN_QUEUE_REPORTS = 2

    fun effective(
        hoursStatus: PumpStatus,
        reports: List<Report>,
        nowMs: Long,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): EffectiveStatus {
        val fresh = reports.filter { it.atMs in (nowMs - windowMs)..nowMs }
        if (fresh.isEmpty()) return EffectiveStatus(hoursStatus, null)

        val newest = fresh.maxByOrNull { it.atMs }!!
        val status = when {
            newest.status == ReportStatus.CLOSED -> PumpStatus.CLOSED
            hoursStatus == PumpStatus.UNKNOWN -> PumpStatus.OPEN
            else -> PumpStatus.OPEN
        }
        return EffectiveStatus(status, crowdOf(fresh))
    }

    private fun crowdOf(fresh: List<Report>): CrowdLevel? {
        val shorts = fresh.count { it.status == ReportStatus.QUEUE_SHORT }
        val longs = fresh.count { it.status == ReportStatus.QUEUE_LONG }
        val total = shorts + longs
        if (total < MIN_QUEUE_REPORTS) return null
        val longShare = longs.toDouble() / total
        return when {
            longShare >= 0.6 -> CrowdLevel.BUSY
            longShare <= 0.25 -> CrowdLevel.QUIET
            else -> CrowdLevel.MODERATE
        }
    }
}
