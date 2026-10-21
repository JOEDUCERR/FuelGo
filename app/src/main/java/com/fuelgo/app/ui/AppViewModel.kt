package com.fuelgo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fuelgo.app.di.AppContainer
import com.fuelgo.app.domain.StatusAggregator
import com.fuelgo.app.domain.hoursStatus
import com.fuelgo.app.domain.model.CrowdLevel
import com.fuelgo.app.domain.model.FuelType
import com.fuelgo.app.domain.model.PumpStatus
import com.fuelgo.app.domain.model.ReportStatus
import com.fuelgo.app.domain.model.Station
import com.fuelgo.app.widget.WidgetRefresher
import com.fuelgo.app.work.scheduleWidgetWork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** One row on the Home screen. */
data class StationUi(
    val station: Station,
    val distanceMeters: Double,
    val status: PumpStatus,
    val crowd: CrowdLevel?,
)

data class AppState(
    val ready: Boolean = false,
    val onboardingDone: Boolean = false,
    val fuelType: FuelType = FuelType.DIESEL,
    val stations: List<StationUi> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val showSettings: Boolean = false,
    val message: String? = null,
    val widgetRefreshMinutes: Int = 15,
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.prefs.flow.collect { snap ->
                _state.update {
                    it.copy(
                        ready = true,
                        onboardingDone = snap.onboardingDone,
                        fuelType = snap.fuelType,
                        widgetRefreshMinutes = snap.widgetRefreshMinutes,
                    )
                }
                loadFromCache()
            }
        }
    }

    fun completeOnboarding(fuelType: FuelType) {
        viewModelScope.launch { container.prefs.completeOnboarding(fuelType) }
    }

    fun setFuelType(fuelType: FuelType) {
        viewModelScope.launch {
            container.prefs.setFuelType(fuelType)
            // The prefs collector re-ranks the cache immediately; follow up
            // with a network refresh so the new fuel type gets fresh ranking
            // and fuel-tag blend from the providers.
            refresh()
        }
    }

    fun setWidgetRefreshMinutes(minutes: Int) {
        viewModelScope.launch {
            container.prefs.setWidgetRefreshMinutes(minutes)
            scheduleWidgetWork(container.appContext, minutes)
        }
    }

    fun setShowSettings(show: Boolean) {
        _state.update { it.copy(showSettings = show) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * Instant render from the Room cache using the last known location. Runs
     * on every prefs change (fuel type switch, etc.) with zero network.
     */
    private fun loadFromCache() {
        viewModelScope.launch {
            val snap = container.prefs.snapshot()
            val lat = snap.lastLat ?: return@launch
            val lon = snap.lastLon ?: return@launch
            val ranked = container.stationRepository.cachedNearby(lat, lon, snap.fuelType)
            val reports = container.reportRepository
                .recentReports(ranked.map { it.first.id })
            val now = System.currentTimeMillis()
            val at = ZonedDateTime.now()
            val rows = ranked.map { (station, distance) ->
                val effective = StatusAggregator.effective(
                    hoursStatus(station.rawOpeningHours, at),
                    reports[station.id] ?: emptyList(),
                    now,
                )
                StationUi(station, distance, effective.status, effective.crowd)
            }
            _state.update { it.copy(stations = rows) }
            WidgetRefresher.updateFromCache(container.appContext)
        }
    }

    /** Location + network refresh; falls back to cache with a soft error. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val snap = container.prefs.snapshot()
            val loc = container.locationClient.currentOrNull()
            if (loc != null) {
                container.prefs.saveLastLocation(loc.latitude, loc.longitude)
            }
            val lat = loc?.latitude ?: snap.lastLat
            val lon = loc?.longitude ?: snap.lastLon
            if (lat == null || lon == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Location unavailable — allow location access and retry.",
                    )
                }
                return@launch
            }
            val networkOk = try {
                container.stationRepository.refresh(lat, lon, snap.fuelType)
            } catch (e: Exception) {
                false
            }
            loadFromCache()
            _state.update {
                it.copy(
                    loading = false,
                    error = if (networkOk) {
                        null
                    } else {
                        val detail = container.stationRepository.lastRefreshError
                        "Couldn't fetch stations" + (detail?.let { d -> " — $d" } ?: "") +
                            ". Tap Try again in a moment."
                    },
                )
            }
        }
    }

    fun onPermissionDenied() {
        _state.update {
            it.copy(error = "Location permission needed to find nearby pumps.")
        }
    }

    /** v2: two-tap crowdsourced report. */
    fun submitReport(station: Station, status: ReportStatus) {
        viewModelScope.launch {
            val loc = container.locationClient.currentOrNull(timeoutMs = 2000)
            val rejection = container.reportRepository.submit(
                stationId = station.id,
                status = status,
                userLat = loc?.latitude,
                userLon = loc?.longitude,
                stationLat = station.lat,
                stationLon = station.lon,
            )
            _state.update { it.copy(message = rejection ?: "Thanks for reporting!") }
            loadFromCache()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
