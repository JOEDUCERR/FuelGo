package com.fuelgo.app.di

import android.content.Context
import android.util.Log
import com.fuelgo.app.BuildConfig
import com.fuelgo.app.data.LocationClient
import com.fuelgo.app.data.api.OverpassApi
import com.fuelgo.app.data.api.PlacesApi
import com.fuelgo.app.data.db.FuelGoDatabase
import com.fuelgo.app.data.prefs.UserPrefs
import com.fuelgo.app.data.repo.ReportRepository
import com.fuelgo.app.data.repo.StationRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

private const val TAG = "FuelGo"

/** Redacts the Places key from request headers before they reach logcat. */
private val API_KEY_REDACTION = Regex("(?i)X-Goog-Api-Key: \\S+")

/**
 * THE single shared Json/serialization configuration for the app's network
 * layer. Its settings are CONTRACTUAL:
 *
 * `encodeDefaults = true` is mandatory. kotlinx.serialization silently omits
 * properties equal to their default value when this is false — which stripped
 * `includedPrimaryTypes`, `rankPreference` and `maxResultCount` from every
 * Places request we ever sent (Google answered "all types nearby" → junk
 * POIs; proven regression, Oct 2026). `PlacesRequestRegressionTest` asserts
 * the wire body and GROWS FAILING if this is ever set back to false.
 */
object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
}

/**
 * Manual dependency container — deliberately tiny; no DI framework at this
 * codebase size.
 */
class AppContainer(val appContext: Context) {

    val prefs = UserPrefs(appContext)
    val database = FuelGoDatabase.build(appContext)
    val locationClient = LocationClient(appContext)

    private val json = AppJson.json

    /** Body-level HTTP logging for debug builds only, API key redacted. */
    private val httpLogger = HttpLoggingInterceptor { message ->
        Log.d(TAG, API_KEY_REDACTION.replace(message, "X-Goog-Api-Key: REDACTED"))
    }.apply { level = HttpLoggingInterceptor.Level.BODY }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Overpass instances can be slow under load; give each mirror time.
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(httpLogger)
            }
        }
        .build()

    private val overpassRetrofit = Retrofit.Builder()
        .baseUrl("https://overpass-api.de/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val overpassApi = overpassRetrofit.create(OverpassApi::class.java)

    /** Null when no key is configured — OSM stays the default provider. */
    private val placesApi: StationRepository.PlacesApiHandle? =
        if (BuildConfig.PLACES_API_KEY.isBlank()) null else placesRetrofit()

    val stationRepository = StationRepository(
        stationDao = database.stationDao(),
        overpassApi = overpassApi,
        placesApi = placesApi,
    )

    val reportRepository = ReportRepository(
        context = appContext,
        reportDao = database.reportDao(),
        prefs = prefs,
    )

    private fun placesRetrofit(): StationRepository.PlacesApiHandle =
        StationRepository.PlacesApiHandle(
            api = Retrofit.Builder()
                .baseUrl("https://places.googleapis.com/")
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(PlacesApi::class.java),
            apiKey = BuildConfig.PLACES_API_KEY,
        )
}
