package com.fuelgo.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands navigation off to Google Maps — FuelGo never navigates in-app.
 * Falls back to the generic geo: intent (any maps app), then to [fallback].
 */
fun Context.startNavigation(lat: Double, lon: Double, fallback: () -> Unit = {}) {
    val uris = listOf(
        "google.navigation:q=$lat,$lon",
        "geo:$lat,$lon?q=$lat,$lon(Fuel)",
    )
    for (uri in uris) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next fallback.
        }
    }
    fallback()
}
