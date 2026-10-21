package com.fuelgo.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fuelgo.app.domain.model.FuelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "fuelgo_prefs")

/** Widget payload: nearest pump + status, rendered without any network call. */
@Serializable
data class WidgetState(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** PumpStatus name. */
    val status: String,
    /** Pre-formatted distance from the user's last known fix, e.g. "1.2 km". */
    val distanceText: String = "",
    /** Selected fuel type name, e.g. "Diesel". */
    val fuelType: String = "",
    val updatedAtMs: Long = 0,
)

class UserPrefs(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    data class Snapshot(
        val onboardingDone: Boolean,
        val fuelType: FuelType,
        val widgetRefreshMinutes: Int,
        val lastLat: Double?,
        val lastLon: Double?,
    )

    val flow: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            onboardingDone = p[KEY_ONBOARDED] ?: false,
            fuelType = FuelType.fromName(p[KEY_FUEL_TYPE]),
            widgetRefreshMinutes = p[KEY_REFRESH_MINUTES] ?: 15,
            lastLat = p[KEY_LAST_LAT],
            lastLon = p[KEY_LAST_LON],
        )
    }

    suspend fun snapshot(): Snapshot = flow.first()

    suspend fun completeOnboarding(fuelType: FuelType) {
        context.dataStore.edit {
            it[KEY_ONBOARDED] = true
            it[KEY_FUEL_TYPE] = fuelType.name
        }
    }

    suspend fun setFuelType(fuelType: FuelType) {
        context.dataStore.edit { it[KEY_FUEL_TYPE] = fuelType.name }
    }

    suspend fun setWidgetRefreshMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_REFRESH_MINUTES] = minutes }
    }

    suspend fun saveLastLocation(lat: Double, lon: Double) {
        context.dataStore.edit {
            it[KEY_LAST_LAT] = lat.toDouble()
            it[KEY_LAST_LON] = lon.toDouble()
        }
    }

    /** Stable anonymous device id for v2 reports (no accounts). */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.first()[KEY_DEVICE_ID]
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_DEVICE_ID] = id }
        return id
    }

    // ---- Widget state ----

    suspend fun saveWidgetState(state: WidgetState) {
        context.dataStore.edit { it[KEY_WIDGET_STATE] = json.encodeToString(state) }
    }

    suspend fun widgetState(): WidgetState? =
        context.dataStore.data.first()[KEY_WIDGET_STATE]?.let { raw ->
            runCatching { json.decodeFromString<WidgetState>(raw) }.getOrNull()
        }

    // ---- Report rate limiting (stationId -> last report ms) ----

    suspend fun lastReportAt(stationId: String): Long? = reportLog()[stationId]

    suspend fun markReported(stationId: String, atMs: Long) {
        val log = reportLog().toMutableMap()
        log[stationId] = atMs
        context.dataStore.edit { it[KEY_REPORT_LOG] = json.encodeToString(log) }
    }

    private suspend fun reportLog(): Map<String, Long> =
        context.dataStore.data.first()[KEY_REPORT_LOG]
            ?.let { raw -> runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrNull() }
            ?: emptyMap()

    private companion object {
        val KEY_ONBOARDED = booleanPreferencesKey("onboarding_done")
        val KEY_FUEL_TYPE = stringPreferencesKey("fuel_type")
        val KEY_REFRESH_MINUTES = intPreferencesKey("widget_refresh_minutes")
        val KEY_LAST_LAT = doublePreferencesKey("last_lat")
        val KEY_LAST_LON = doublePreferencesKey("last_lon")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_WIDGET_STATE = stringPreferencesKey("widget_state")
        val KEY_REPORT_LOG = stringPreferencesKey("report_log")
    }
}
