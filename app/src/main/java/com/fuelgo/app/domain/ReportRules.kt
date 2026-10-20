package com.fuelgo.app.domain

/**
 * Client-side rate limiting for crowdsourced reports: at most one report per
 * device per station per [COOLDOWN_MS]. Pure function, unit-tested.
 */
object ReportRules {

    const val COOLDOWN_MS: Long = 10 * 60 * 1000L

    /** Max distance (meters) from a station for a report about it to count. */
    const val MAX_REPORT_DISTANCE_M = 300.0

    fun canReport(lastReportAtMs: Long?, nowMs: Long, cooldownMs: Long = COOLDOWN_MS): Boolean =
        lastReportAtMs == null || nowMs - lastReportAtMs >= cooldownMs

    fun withinRange(distanceMeters: Double): Boolean =
        distanceMeters <= MAX_REPORT_DISTANCE_M
}
