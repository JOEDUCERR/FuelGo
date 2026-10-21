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
 * [PlacesFuelFilter]: positive-evidence verification with a never-blank
 * fallback, so bad categorization can show junk results but NEVER an
 * empty station list.
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
     * Positive-evidence fuel-pump check: Google tells us this business IS
     * categorized as a petrol pump (primary or listed types). Requires
     * affirmative gas_station categorization — absence of evidence is NOT
     * evidence of a pump.
     */
    val isCategorizedFuelPump: Boolean
        get() = primaryType == "gas_station" || types.contains("gas_station")
}

/**
 * Client-side final guard over Places results, defined by the researched
 * semantics of Google's India data:
 *
 * 1. Keep only places Google categorizes as petrol pumps
 *    ([PlaceDto.isCategorizedFuelPump]) — this drops junk whose stray
 *    secondary `gas_station` type slipped past the request filter.
 * 2. NEVER return an empty list when raw results were non-empty: if
 *    verification disqualifies everything (a Google data quirk), fall back
 *    to the raw results. Junk may be visible, but a taxi driver always gets
 *    the screen's core function — a station list — rather than nothing.
 */
object PlacesFuelFilter {
    fun select(raw: List<PlaceDto>): List<PlaceDto> {
        val verified = raw.filter { it.isCategorizedFuelPump }
        return if (verified.isNotEmpty() || raw.isEmpty()) verified else raw
    }
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
