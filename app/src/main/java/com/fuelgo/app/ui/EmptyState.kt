package com.fuelgo.app.ui

/**
 * Pure selection of the Home screen's zero-data state, expressed so the four
 * cold-start situations can't drift apart (covered by EmptyStateTest):
 *
 * (a) location permission missing  -> clear message + grant/settings actions
 * (b) permission granted, no fix   -> "Getting your location…"
 * (c) location OK, providers failed -> short failure summary + Retry
 * (d) providers OK, zero stations  -> honest "no stations found" notice
 */
enum class LastOutcome { PROVIDERS_FAILED, OK_EMPTY, OK_DATA }

enum class EmptyStateKind {
    PERMISSION_MISSING,
    WAITING_LOCATION,
    PROVIDERS_FAILED,
    NO_STATIONS_FOUND,
}

enum class EmptyStateAction { NONE, RETRY, GRANT, OPEN_SETTINGS }

data class EmptyStateUi(
    val kind: EmptyStateKind,
    val title: String,
    val detail: String? = null,
    val primaryAction: EmptyStateAction = EmptyStateAction.NONE,
)

/**
 * [hasPermission] null = not yet resolved (activity still checking).
 * [hasFix] = a location (last-known or fresh) is available.
 * [outcome] = the last network attempt's summarized result; null = none yet.
 */
fun emptyStateFor(
    hasPermission: Boolean?,
    hasFix: Boolean,
    loading: Boolean,
    outcome: LastOutcome?,
): EmptyStateUi? = when {
    hasPermission == false -> EmptyStateUi(
        kind = EmptyStateKind.PERMISSION_MISSING,
        title = "Location permission needed",
        detail = "FuelGo needs your location to find pumps near you.",
        primaryAction = EmptyStateAction.GRANT,
    )
    hasPermission == null -> null
    !hasFix && loading -> EmptyStateUi(
        kind = EmptyStateKind.WAITING_LOCATION,
        title = "Getting your location…",
    )
    !hasFix -> EmptyStateUi(
        kind = EmptyStateKind.WAITING_LOCATION,
        title = "Getting your location…",
        detail = "Waiting for GPS. Make sure location services are on.",
        primaryAction = EmptyStateAction.RETRY,
    )
    outcome == LastOutcome.PROVIDERS_FAILED -> EmptyStateUi(
        kind = EmptyStateKind.PROVIDERS_FAILED,
        title = "Couldn't reach the station data sources",
        detail = "The error banner above shows what failed. Retrying usually helps.",
        primaryAction = EmptyStateAction.RETRY,
    )
    outcome == LastOutcome.OK_EMPTY -> EmptyStateUi(
        kind = EmptyStateKind.NO_STATIONS_FOUND,
        title = "No fuel stations found within 10 km.",
        detail = "Move or retry — the data sources may be busy.",
        primaryAction = EmptyStateAction.RETRY,
    )
    else -> null
}
