package com.fuelgo.app.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OverpassApi {

    /** Free, keyless OSM Overpass API — FuelGo's default station provider. */
    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun interpret(@Field("data") query: String): OverpassResponse
}

@Serializable
data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

@Serializable
data class OverpassElement(
    val type: String? = null,
    val id: Long = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
data class OverpassCenter(val lat: Double, val lon: Double)

object OverpassQueries {

    private const val RADIUS_M = 5000

    fun fuelAround(lat: Double, lon: Double): String =
        """
        [out:json][timeout:15];
        (
          node["amenity"="fuel"](around:$RADIUS_M,$lat,$lon);
          way["amenity"="fuel"](around:$RADIUS_M,$lat,$lon);
        );
        out center 80;
        """.trimIndent()
}
