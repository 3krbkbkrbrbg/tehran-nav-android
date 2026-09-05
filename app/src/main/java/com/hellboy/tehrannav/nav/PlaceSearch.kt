package com.hellboy.tehrannav.nav

import com.hellboy.tehrannav.data.GeocodeFeature
import com.hellboy.tehrannav.data.GeocodeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Place search + reverse geocoding via Nominatim (OpenStreetMap). */
object PlaceSearch {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Place(val name: String, val lat: Double, val lon: Double)

    /** Forward geocode a Persian/English query, bias toward Tehran. */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$q&format=jsonv2&limit=8&accept-language=fa&viewbox=51.0,35.9,51.7,35.5&bounded=1&countrycodes=ir"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "TehranNav/1.0 (contact: hellboy)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                val parsed = json.decodeFromString<List<GeocodeFeature>>(body)
                parsed.mapNotNull { f ->
                    val c = f.geometry?.coordinates
                    if (c == null || c.size < 2) return@mapNotNull null
                    val name = buildString {
                        append(f.properties.name.ifBlank { f.properties.street ?: "مکان" })
                        val district = f.properties.district ?: f.properties.city ?: f.properties.locality
                        if (!district.isNullOrBlank()) append(" — $district")
                        if (!f.properties.country.isNullOrBlank()) append(" (${f.properties.country})")
                    }
                    Place(name, c[1], c[0])
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Reverse geocode current GPS position → address text (Persian). */
    suspend fun reverse(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2&accept-language=fa"
            val req = Request.Builder().url(url).header("User-Agent", "TehranNav/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val body = resp.body?.string().orEmpty()
                // extract display_name only — no need for full model
                val p = json.parseToJsonElement(body).jsonObject
                p["display_name"]?.toString()?.trim('"') ?: ""
            }
        }.getOrDefault("")
    }
}