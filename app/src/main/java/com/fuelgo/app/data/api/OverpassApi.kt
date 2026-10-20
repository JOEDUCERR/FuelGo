package com.fuelgo.app.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

interface OverpassApi {

    /** Free, keyless OSM Overpass API — FuelGo's default station provider. */
    @FormUrlEncoded
    @POST
    suspend fun interpret(@Url url: String, @Field("data") query: String): OverpassResponse
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

/**
 * Overpass endpoints tried in order. Instances are community-run and throttle
 * or refuse datacenter IPs, so the app falls back across mirrors.
 */
object OverpassMirrors {
    val urls = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
}
