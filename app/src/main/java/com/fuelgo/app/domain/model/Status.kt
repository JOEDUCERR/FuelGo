package com.fuelgo.app.domain.model

/** Open/closed state of a pump. */
enum class PumpStatus(val displayName: String) {
    OPEN("Open"),
    CLOSED("Closed"),
    UNKNOWN("Hours unknown");

    /** ARGB color used by both the app list and the widget dot. */
    val argb: Long
        get() = when (this) {
            OPEN -> 0xFF2E7D32
            CLOSED -> 0xFFC62828
            UNKNOWN -> 0xFF9E9E9E
        }
}

/** Coarse crowd level derived from recent queue reports. */
enum class CrowdLevel(val displayName: String) {
    QUIET("Quiet"),
    MODERATE("Moderate"),
    BUSY("Busy"),
}

/** What a user can report about a pump with two taps. */
enum class ReportStatus(val displayName: String) {
    OPEN("Open now"),
    CLOSED("Closed"),
    QUEUE_SHORT("Small queue"),
    QUEUE_LONG("Long queue");

    val impliesOpen: Boolean
        get() = this != CLOSED
}

/** A single crowdsourced report. */
data class Report(
    val stationId: String,
    val status: ReportStatus,
    val atMs: Long,
)

/** Final status shown to the user: hours-derived status plus crowd level. */
data class EffectiveStatus(
    val status: PumpStatus,
    val crowd: CrowdLevel?,
)
