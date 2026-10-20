package com.fuelgo.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Minimal location access via the platform LocationManager — no Play Services
 * dependency, keeping the app runnable on any device.
 *
 * Returns the freshest fix available within [timeoutMs], falling back to the
 * most recent last-known fix. Callers must hold location permission.
 */
class LocationClient(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun currentOrNull(timeoutMs: Long = 5000L): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = sequenceOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = LocationListener { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                try {
                    val provider = bestProvider(lm)
                    if (Build.VERSION.SDK_INT >= 30) {
                        lm.getCurrentLocation(
                            provider,
                            null,
                            ContextCompat.getMainExecutor(context),
                        ) { loc ->
                            if (loc != null && cont.isActive) cont.resume(loc)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(
                            provider,
                            listener,
                            Looper.getMainLooper(),
                        )
                    }
                } catch (e: Exception) {
                    // No provider available; fall through to timeout/last-known.
                }
            }
        }
        return fresh ?: last
    }

    private fun bestProvider(lm: LocationManager): String =
        when {
            Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) ->
                LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.GPS_PROVIDER
        }
}
