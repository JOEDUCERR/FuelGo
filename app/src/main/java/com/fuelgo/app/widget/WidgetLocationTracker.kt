package com.fuelgo.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Passive location tracking for the widget: listens on the PASSIVE provider —
 * updates only arrive while OTHER apps use GPS, so there is effectively no
 * extra battery cost. Each accepted fix re-renders the widget (distance left,
 * nearest pump, status) from cache. 60 s / 25 m thresholds rate-limit updates
 * to something useful for a moving taxi without spamming recomputation.
 *
 * Turn-by-turn navigation itself cannot live inside a 1x1 widget (Android
 * requires a foreground app for it); the widget shows the minimal live info
 * and tapping still hands off to Google Maps for directions.
 */
object WidgetLocationTracker {

    private var registered = false
    private val scope = CoroutineScope(Dispatchers.Default)

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (registered) return
        registered = true
        val appContext = context.applicationContext
        try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = LocationListenerCompat { _ ->
                scope.launch { WidgetRefresher.updateFromCache(appContext) }
            }
            @SuppressLint("MissingPermission")
            lm.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                60_000L,   // min 60 s between widget updates
                25f,       // or 25 m of movement
                listener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            // No location permission yet — the app-open and periodic refresh
            // paths still keep the widget updated.
        } catch (e: Exception) {
            // Passive provider unavailable; degrade silently.
        }
    }
}
