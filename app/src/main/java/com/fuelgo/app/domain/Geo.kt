package com.fuelgo.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {

    private const val EARTH_RADIUS_M = 6371000.0

    /** Great-circle distance in meters between two WGS84 points. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /** Human-readable distance: meters below 1 km, else km with one decimal. */
    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        else -> String.format("%.1f km", meters / 1000)
    }
}
