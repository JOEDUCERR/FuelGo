package com.fuelgo.app.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Google Places API (New) — Nearby Search. Used alongside OSM when a
 * `PLACES_API_KEY` is configured.
 *
 * NOTE: the client intentionally does NOT re-verify the returned POIs'
 * categories. The request already filters server-side by gas_station;
 * a client-side re-filter can only ever shrink the list toward zero when
 * Google's India categorization is imperfect — which emptied station lists in
 * the field. Data-quality junk (if any) is visible by name instead.
 */
interface PlacesApi {

    @POST("v1/places:searchNearby")
    suspend fun searchNearby(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String =
            "places.id,places.displayName,places.location,places.types,places.primaryType,places.regularOpeningHours",
        @Body body: NearbySearchRequest,
    ): PlacesResponse
}

@Serializable
data class NearbySearchRequest(
    val includedTypes: List<String> = listOf("gas_station"),
    val maxResultCount: Int = 20,
    val locationRestriction: LocationRestriction,
    val rankPreference: String = "DISTANCE",
)

@Serializable
data class LocationRestriction(val circle: Circle)

@Serializable
data class Circle(val center: Center, val radius: Double)

@Serializable
data class Center(val latitude: Double, val longitude: Double)

@Serializable
data class PlacesResponse(val places: List<PlaceDto> = emptyList())

@Serializable
data class PlaceDto(
    val id: String,
    val displayName: DisplayNameDto? = null,
    val location: LocationDto? = null,
    val regularOpeningHours: RegularOpeningHoursDto? = null,
    val types: List<String> = emptyList(),
    val primaryType: String? = null,
)

@Serializable
data class DisplayNameDto(val text: String? = null)

@Serializable
data class LocationDto(val latitude: Double, val longitude: Double)

@Serializable
data class RegularOpeningHoursDto(val periods: List<PeriodDto> = emptyList())

@Serializable
data class PeriodDto(val open: TimePointDto? = null, val close: TimePointDto? = null)

@Serializable
data class TimePointDto(val day: Int? = null, val time: String? = null)
