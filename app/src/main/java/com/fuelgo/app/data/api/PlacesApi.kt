package com.fuelgo.app.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Google Places API (New) — Nearby Search. Used alongside OSM when a
 * `PLACES_API_KEY` is configured.
 *
 * RESEARCHED: how Google categorizes Indian petrol pumps.
 * - Every place has exactly ONE primary type (Table A) plus any number of
 *   secondary types. A real Indian petrol pump's primary type is
 *   `gas_station` (displayed as "Petrol pump" in Google Maps India).
 * - `includedTypes` matches ANY type in a place's `types` list, including
 *   secondary ones — so imperfect India data lets miscategorized businesses
 *   (offices, boiler plants) match on a stray secondary type and surface as
 *   junk pump results.
 * - `includedPrimaryTypes` matches ONLY the primary type, i.e. places that
 *   genuinely are petrol pumps. That is what this request uses.
 *
 * On top of the server-side primary-type filter, results pass through
 * [PlacesFuelFilter]: primary-type-only verification. If verification empties
 * the Places results, the repository relies on OSM results only — with an
 * honest empty state when both providers have nothing. Unverified Places
 * POIs are never shown.
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
    /**
     * Strict primary-type filter. Docs (developers.google.com/maps/
     * documentation/places/web-service/nearby-search):
     * "Use includedPrimaryTypes and excludedPrimaryTypes to filter the
     * results on a place's primary type."
     */
    val includedPrimaryTypes: List<String> = listOf("gas_station"),
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
) {
    /**
     * Fuel-pump verification, PRIMARY TYPE ONLY: a place counts as a petrol
     * pump exclusively when Google's PRIMARY category is `gas_station`. A
     * secondary `gas_station` in `types[]` must NOT pass — Google's India
     * data lets offices/boiler plants carry stray secondary types, which
     * produced field junk like "Cheema Boilers Limited".
     */
    val isCategorizedFuelPump: Boolean
        get() = primaryType == "gas_station"
}

/**
 * Client-side final guard over Places results:
 *
 * Keep ONLY places whose primary type is `gas_station`
 * ([PlaceDto.isCategorizedFuelPump]). This is a strict filter — it CAN return
 * an empty list. When verification empties the Places results, the repository
 * relies on OSM/Overpass results only (with an honest "no stations found"
 * state if those are empty too). Unverified Places POIs are NEVER shown.
 */
object PlacesFuelFilter {
    fun select(raw: List<PlaceDto>): List<PlaceDto> = raw.filter { it.isCategorizedFuelPump }
}

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
