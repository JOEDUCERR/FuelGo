package com.fuelgo.app.di

import android.content.Context
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
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container — deliberately tiny; no DI framework at this
 * codebase size.
 */
class AppContainer(val appContext: Context) {

    val prefs = UserPrefs(appContext)
    val database = FuelGoDatabase.build(appContext)
    val locationClient = LocationClient(appContext)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Overpass instances can be slow under load; give each mirror time.
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val overpassRetrofit = Retrofit.Builder()
        .baseUrl("https://overpass-api.de/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val overpassApi = overpassRetrofit.create(OverpassApi::class.java)

    /** Null when no key is configured — OSM stays the default provider. */
    private val placesApi: PlacesApi? =
        if (BuildConfig.PLACES_API_KEY.isBlank()) null else placesRetrofit()

    val stationRepository = StationRepository(
        stationDao = database.stationDao(),
        overpassApi = overpassApi,
        placesApi = placesApi,
        placesApiKey = BuildConfig.PLACES_API_KEY.ifBlank { null },
    )

    val reportRepository = ReportRepository(
        context = appContext,
        reportDao = database.reportDao(),
        prefs = prefs,
    )

    private fun placesRetrofit(): PlacesApi =
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlacesApi::class.java)
}
