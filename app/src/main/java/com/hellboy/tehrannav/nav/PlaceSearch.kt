package com.hellboy.tehrannav.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * جست‌وجوی آدرس در سراسر ایران و تبدیل مختصات به نشانی فارسی.
 * منبع فعلی Nominatim/OpenStreetMap است و به API Key نیاز ندارد.
 * برای رعایت محدودیت سرویس عمومی، UI درخواست‌ها را debounce می‌کند.
 */
object PlaceSearch {

    private const val BASE_URL = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "TehranNav/2.0 (OpenStreetMap navigation app)"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class SearchItem(
        val lat: String = "",
        val lon: String = "",
        val display_name: String = "",
        val name: String = "",
        val type: String = "",
        val category: String = ""
    )

    data class Place(
        val name: String,
        val lat: Double,
        val lon: Double,
        val subtitle: String = "",
        val type: String = ""
    )

    /** جست‌وجوی آدرس، مکان، خیابان یا کسب‌وکار در کل محدوده کشور ایران. */
    suspend fun search(query: String, limit: Int = 8): List<Place> = withContext(Dispatchers.IO) {
        val normalized = normalize(query)
        if (normalized.length < 2) return@withContext emptyList()

        runCatching {
            val q = URLEncoder.encode(normalized, "UTF-8")
            val url = "$BASE_URL/search" +
                "?q=$q&format=jsonv2&addressdetails=1&dedupe=1" +
                "&limit=${limit.coerceIn(1, 10)}&accept-language=fa&countrycodes=ir"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                json.decodeFromString<List<SearchItem>>(body).mapNotNull { item ->
                    val lat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = item.lon.toDoubleOrNull() ?: return@mapNotNull null
                    val full = item.display_name.trim()
                    val title = item.name.trim().ifBlank {
                        full.substringBefore(",").trim().ifBlank { "مکان انتخاب‌شده" }
                    }
                    val subtitle = full.removePrefix(title).trim().trimStart(',', '،', ' ')
                    Place(title, lat, lon, subtitle, item.type.ifBlank { item.category })
                }.distinctBy { "%.5f,%.5f".format(it.lat, it.lon) }
            }
        }.getOrDefault(emptyList())
    }

    /** تبدیل مختصات فعلی به نشانی فارسی برای نمایش به‌عنوان مبدأ. */
    suspend fun reverse(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE_URL/reverse?lat=$lat&lon=$lon&format=jsonv2" +
                "&addressdetails=1&accept-language=fa"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                val body = response.body?.string().orEmpty()
                json.parseToJsonElement(body).jsonObject["display_name"]
                    ?.toString()?.trim('"') ?: ""
            }
        }.getOrDefault("")
    }

    /** یکسان‌سازی حروف فارسی/عربی برای جست‌وجوی دقیق‌تر. */
    private fun normalize(value: String): String = value
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ۀ', 'ه')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
