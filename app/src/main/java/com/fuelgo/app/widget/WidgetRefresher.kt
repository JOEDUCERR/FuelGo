package com.fuelgo.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.fuelgo.app.FuelGoApp
import com.fuelgo.app.data.prefs.WidgetState
import com.fuelgo.app.domain.Geo
import com.fuelgo.app.domain.StatusAggregator
import com.fuelgo.app.domain.hoursStatus
import java.time.ZonedDateTime

/**
 * Computes the widget payload and pushes it to all widget instances.
 *
 * Two entry points:
 *  - [updateFromCache]: zero network; used on app open and after refreshes.
 *  - [fullRefresh]: best-effort network refresh + location, used by WorkManager.
 */
object WidgetRefresher {

    suspend fun updateFromCache(context: Context) {
        val container = (context.applicationContext as FuelGoApp).container
        val snap = container.prefs.snapshot()
        val lat = snap.lastLat
        val lon = snap.lastLon
        if (lat != null && lon != null) {
            val ranked = container.stationRepository.cachedNearby(lat, lon, snap.fuelType)
            val nearest = ranked.firstOrNull()
            if (nearest != null) {
                val (station, distanceMeters) = nearest
                val now = System.currentTimeMillis()
                val reports = container.reportRepository
                    .recentReports(listOf(station.id))[station.id] ?: emptyList()
                val effective = StatusAggregator.effective(
                    hoursStatus(station.rawOpeningHours, ZonedDateTime.now()),
                    reports,
                    now,
                )
                container.prefs.saveWidgetState(
                    WidgetState(
                        name = station.name,
                        lat = station.lat,
                        lon = station.lon,
                        status = effective.status.name,
                        distanceText = Geo.formatDistance(distanceMeters),
                        fuelType = snap.fuelType.displayName,
                        updatedAtMs = now,
                    ),
                )
            }
        }
        FuelWidget.updateAll(context)
    }

    suspend fun fullRefresh(context: Context) {
        val container = (context.applicationContext as FuelGoApp).container
        val snap = container.prefs.snapshot()
        val loc = container.locationClient.currentOrNull()
        if (loc != null) {
            container.prefs.saveLastLocation(loc.latitude, loc.longitude)
            runCatching {
                container.stationRepository.refresh(loc.latitude, loc.longitude, snap.fuelType)
            }
        }
        updateFromCache(context)
    }
}
