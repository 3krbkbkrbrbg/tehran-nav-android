package com.hellboy.tehrannav.data

import kotlinx.serialization.Serializable

@Serializable
data class LatLngDto(val lat: Double, val lon: Double)

@Serializable
data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute> = emptyList(),
    val waypoints: List<OsrmWaypoint> = emptyList()
)

@Serializable
data class OsrmRoute(
    val geometry: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val legs: List<OsrmLeg> = emptyList()
)

@Serializable
data class OsrmLeg(val steps: List<OsrmStep> = emptyList())

@Serializable
data class OsrmStep(
    val geometry: String = "",
    val name: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val maneuver: OsrmManeuver = OsrmManeuver()
)

@Serializable
data class OsrmManeuver(
    val type: String = "",
    val modifier: String? = null,
    val location: List<Double> = emptyList(),
    val bearing_after: Int = 0
)

@Serializable
data class OsrmWaypoint(val location: List<Double> = emptyList())

@Serializable
data class GeocodeResponse(val features: List<GeocodeFeature> = emptyList())

@Serializable
data class GeocodeFeature(
    val geometry: GeocodeGeometry? = null,
    val properties: GeocodeProperties = GeocodeProperties()
)

@Serializable
data class GeocodeGeometry(val coordinates: List<Double> = emptyList())

@Serializable
data class GeocodeProperties(
    val name: String = "",
    val locality: String? = null,
    val district: String? = null,
    val street: String? = null,
    val address: String? = null,
    val country: String? = null,
    val city: String? = null
)

/** Decoded OSRM polyline → list of (lat, lon) */
fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng

        points.add(Pair(lat / 1e5, lng / 1e5))
    }
    return points
}