package com.hellboy.tehrannav.nav

import android.util.Log
import com.hellboy.tehrannav.data.OsrmRoute
import com.hellboy.tehrannav.data.OsrmRouteResponse
import com.hellboy.tehrannav.data.OsrmStep
import com.hellboy.tehrannav.data.decodePolyline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A decoded navigable route: full polyline + steps with Persian instructions */
class Route {
    var points: List<Pair<Double, Double>> = emptyList()
    var steps: List<NavStep> = emptyList()
    var totalDistance = 0.0
    var totalDuration = 0.0

    class NavStep(
        val name: String,
        val street: String,
        val instruction: String,
        val distance: Double,
        val maneuverType: String,
        val modifier: String?,
        val locationStart: Pair<Double, Double>,
        val locationEnd: Pair<Double, Double>,
        val bearingAfter: Int
    )
}

object NavEngine {
    private const val TAG = "NavEngine"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // OSRM public demo server; car profile → drivable roads of Tehran.
    private const val OSRM_BASE = "https://router.project-osrm.org"

    suspend fun route(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Result<Route> = withContext(Dispatchers.IO) {
        try {
            val url = "$OSRM_BASE/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat" +
                "?overview=full&geometries=polyline&steps=true&annotations=false"
            val req = Request.Builder().url(url).header("User-Agent", "TehranNav/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("OSRM HTTP ${resp.code}"))
                }
                val body = resp.body?.string().orEmpty()
                val parsed = json.decodeFromString<OsrmRouteResponse>(body)
                if (parsed.code != "Ok" || parsed.routes.isEmpty()) {
                    return@withContext Result.failure(Exception("OSRM: ${parsed.code}"))
                }
                val osrmRoute: OsrmRoute = parsed.routes.first()
                val route = Route()
                route.points = decodePolyline(osrmRoute.geometry)
                route.totalDistance = osrmRoute.distance
                route.totalDuration = osrmRoute.duration
                route.steps = osrmRoute.legs.flatMap { it.steps }.mapNotNull { step ->
                    val pts = decodePolyline(step.geometry)
                    if (pts.isEmpty()) return@mapNotNull null
                    Route.NavStep(
                        name = step.name,
                        street = step.name,
                        instruction = buildInstruction(step),
                        distance = step.distance,
                        maneuverType = step.maneuver.type,
                        modifier = step.maneuver.modifier,
                        locationStart = pts.first(),
                        locationEnd = pts.last(),
                        bearingAfter = step.maneuver.bearing_after
                    )
                }
                Result.success(route)
            }
        } catch (e: Exception) {
            Log.e(TAG, "route failed", e)
            Result.failure(e)
        }
    }

    private val FA_MAP = mapOf(
        "turn" to "بپیچید", "continue" to "ادامه دهید", "depart" to "شروع حرکت کنید",
        "arrive" to "به مقصد رسیدید", "new name" to "ادامه دهید", "end of road" to "به انتهای خیابان رسیدید"
    )
    private val MOD_MAP = mapOf(
        "left" to "به چپ", "slight left" to "کمی به چپ", "sharp left" to "دور به چپ",
        "right" to "به راست", "slight right" to "کمی به راست", "sharp right" to "دور به راست",
        "straight" to "مستقیم", "uturn" to "برگردید (دور برگردان)"
    )

    private fun buildInstruction(step: OsrmStep): String {
        val t = step.maneuver.type
        val m = step.maneuver.modifier
        val sign = when (t) {
            "turn", "continue", "depart", "arrive", "end of road" -> FA_MAP[t] ?: "ادامه دهید"
            "new name" -> "به خیابان ${step.name} وارد شوید"
            else -> "ادامه دهید"
        }
        val dir = m?.let { MOD_MAP[it] } ?: ""
        val street = step.name.trim()
        return when {
            t == "arrive" -> sign
            street.isNotEmpty() -> "$sign $dir — ${street}"
            dir.isNotEmpty() -> "$sign $dir"
            else -> sign
        }
    }

    /** Distance (meters) between two geo points (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(m: Double): String =
        if (m >= 1000) "%.1f کیلومتر".format(m / 1000) else "%.0f متر".format(m)

    fun formatDuration(s: Double): String {
        val min = (s / 60).toInt()
        return if (min >= 60) "${min / 60} ساعت و ${min % 60} دقیقه" else "$min دقیقه"
    }

    /** Remaining distance along the route from current position (in meters). */
    fun remainingDistance(route: Route, lat: Double, lon: Double): Double {
        var best = Double.MAX_VALUE
        var bestIdx = 0
        val pts = route.points
        for (i in pts.indices step 2) {
            val d = distanceMeters(lat, lon, pts[i].first, pts[i].second)
            if (d < best) { best = d; bestIdx = i }
        }
        var rem = 0.0
        for (i in bestIdx until pts.size - 1) {
            rem += distanceMeters(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
        }
        return rem
    }

    /** Index of the next upcoming maneuver step from current position. */
    fun nextStepIndex(route: Route, lat: Double, lon: Double): Int {
        val pts = route.points
        var best = Double.MAX_VALUE
        var bestIdx = 0
        for (i in pts.indices step 2) {
            val d = distanceMeters(lat, lon, pts[i].first, pts[i].second)
            if (d < best) { best = d; bestIdx = i }
        }
        // find the first step whose start point is at/after the matched route point
        for (s in route.steps.indices) {
            val start = route.steps[s].locationStart
            var startIdx = -1
            for (i in pts.indices) {
                if (distanceMeters(start.first, start.second, pts[i].first, pts[i].second) < 25) {
                    startIdx = i; break
                }
            }
            if (startIdx >= bestIdx) return s
        }
        return route.steps.lastIndex.coerceAtLeast(0)
    }

    /** Bearing (0-360) from point A to B. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Turn instruction relative to current heading. */
    fun turnText(heading: Double, targetBearing: Double): String {
        val diff = ((targetBearing - heading + 540.0) % 360.0) - 180.0
        return when {
            abs(diff) < 15 -> "مستقیم"
            diff > 0 && diff < 60 -> "کمی به راست"
            diff >= 60 && diff < 130 -> "به راست بپیچید"
            diff >= 130 -> "دور به راست بپیچید"
            diff < 0 && diff > -60 -> "کمی به چپ"
            diff <= -60 && diff > -130 -> "به چپ بپیچید"
            else -> "دور به چپ بپیچید"
        }
    }

    /** Parse destination from free text (Persian) — Gemini handles this when key present. */
    fun parseDestinationFallback(text: String): String {
        var t = text.trim()
        t = t.replace(Regex("^(بزن|برو|بریم|بریم به|برو به|میخوام برم|میخواهم بروم|لطفا برو|نشون بده|مسیریابی|ناوبری|مسیر|بسمسیر|بیا بریم|برو سمت|برو به سمت|بریم سمت)\\s*"), "")
        t = t.replace(Regex("\\s*(بدون ترافیک|با کمترین ترافیک|از مسیر خلوت|سریعترین مسیر|کوتاهترین مسیر|الان|حالا|لطفا)\\s*$"), "")
        return t.trim()
    }
}