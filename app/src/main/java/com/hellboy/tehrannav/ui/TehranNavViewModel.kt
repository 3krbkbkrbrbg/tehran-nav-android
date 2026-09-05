package com.hellboy.tehrannav.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hellboy.tehrannav.data.Settings
import com.hellboy.tehrannav.nav.NavEngine
import com.hellboy.tehrannav.nav.PlaceSearch
import com.hellboy.tehrannav.nav.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

enum class SearchTarget { ORIGIN, DESTINATION }

data class UiState(
    val originQuery: String = "",
    val originName: String = "موقعیت فعلی",
    val originLat: Double? = null,
    val originLon: Double? = null,
    val destinationQuery: String = "",
    val searchTarget: SearchTarget = SearchTarget.DESTINATION,
    val searchResults: List<PlaceSearch.Place> = emptyList(),
    val searching: Boolean = false,
    val destName: String = "",
    val destLat: Double = 0.0,
    val destLon: Double = 0.0,
    val distance: String = "",
    val time: String = "",
    val guidance: String = "",
    val showOffline: Boolean = false,
    val showCoords: Boolean = false
)

class TehranNavViewModel(app: Application) : AndroidViewModel(app) {

    var state by mutableStateOf(UiState())
        private set

    val settings = Settings(app)

    /** reference to host activity for map operations; set by UI */
    var host: MapHost? = null

    interface MapHost {
        fun currentLocation(): GeoPoint?
        fun drawRoute(route: Route)
        fun zoomToRoute(route: Route)
        fun placeDestMarker(lat: Double, lon: Double, name: String)
        fun clearRoute()
        fun goTo(lat: Double, lon: Double)
        fun startNav()
        fun stopNav()
        fun speak(text: String)
        fun hasLocation(): Boolean
        fun ensurePermissions()
    }

    private var searchJob: Job? = null

    // ---------- جستجوی آدرس ایران ----------
    fun selectTarget(target: SearchTarget) {
        state = state.copy(searchTarget = target, searchResults = emptyList())
    }

    fun onSearchType(q: String) {
        val target = state.searchTarget
        state = if (target == SearchTarget.ORIGIN) {
            state.copy(originQuery = q)
        } else {
            state.copy(destinationQuery = q)
        }
        searchJob?.cancel()
        if (q.trim().length < 2) {
            state = state.copy(searchResults = emptyList(), searching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(450)
            state = state.copy(searching = true)
            val results = PlaceSearch.search(q)
            state = state.copy(searching = false, searchResults = results)
        }
    }

    fun search(q: String = currentQuery()) {
        onSearchType(q)
    }

    fun currentQuery(): String = if (state.searchTarget == SearchTarget.ORIGIN) state.originQuery else state.destinationQuery

    fun dismissResults() {
        state = state.copy(searchResults = emptyList())
    }

    fun useCurrentLocationAsOrigin() {
        val loc = host?.currentLocation()
        state = state.copy(
            originQuery = "",
            originName = "موقعیت فعلی",
            originLat = loc?.latitude,
            originLon = loc?.longitude,
            searchResults = emptyList()
        )
    }

    fun swapOriginDestination() {
        state = state.copy(
            originQuery = state.destinationQuery,
            originName = state.destName.ifBlank { state.destinationQuery },
            originLat = if (state.destLat != 0.0) state.destLat else state.originLat,
            originLon = if (state.destLon != 0.0) state.destLon else state.originLon,
            destinationQuery = state.originName,
            destName = state.originName,
            destLat = state.originLat ?: 0.0,
            destLon = state.originLon ?: 0.0
        )
    }

    fun setShowOffline(v: Boolean) {
        state = state.copy(showOffline = v)
    }

    fun setShowCoords(v: Boolean) {
        state = state.copy(showCoords = v)
    }

    // ---------- destination & routing ----------
    fun pickPlace(place: PlaceSearch.Place) {
        if (state.searchTarget == SearchTarget.ORIGIN) {
            state = state.copy(
                originQuery = place.name,
                originName = place.name,
                originLat = place.lat,
                originLon = place.lon,
                searchResults = emptyList()
            )
            return
        }
        pickDestination(place.name, place.lat, place.lon)
    }

    fun pickDestination(name: String, lat: Double, lon: Double) {
        state = state.copy(
            destinationQuery = "",
            destName = name, destLat = lat, destLon = lon,
            searchResults = emptyList()
        )
        (host as? com.hellboy.tehrannav.MainActivity)?.setDest(name, lat, lon)
        host?.placeDestMarker(lat, lon, name)
        host?.goTo(lat, lon)
        computeRoute(lat, lon, name)
    }

    fun computeRoute(toLat: Double, toLon: Double, name: String) {
        viewModelScope.launch {
            state = state.copy(searching = true)
            val selectedOrigin = if (state.originLat != null && state.originLon != null) {
                GeoPoint(state.originLat!!, state.originLon!!)
            } else {
                host?.currentLocation() ?: GeoPoint(35.6892, 51.3890)
            }
            NavEngine.route(selectedOrigin.latitude, selectedOrigin.longitude, toLat, toLon).fold(
                onSuccess = { route ->
                    state = state.copy(
                        searching = false,
                        distance = NavEngine.formatDistance(route.totalDistance),
                        time = NavEngine.formatDuration(route.totalDuration),
                        guidance = "فاصله کل: ${NavEngine.formatDistance(route.totalDistance)} • " +
                            "حدود ${NavEngine.formatDuration(route.totalDuration)} • " +
                            "${route.steps.size} مرحله"
                    )
                    host?.drawRoute(route)
                    host?.zoomToRoute(route)
                },
                onFailure = {
                    state = state.copy(searching = false, guidance = "خطا در محاسبه مسیر: ${it.message}")
                }
            )
        }
    }

    // ---------- navigation control ----------
    fun beginNav() {
        host?.startNav()
    }

    fun endNav() {
        host?.stopNav()
    }

    fun clearAll() {
        state = state.copy(destName = "", distance = "", time = "", guidance = "")
        host?.clearRoute()
    }

    fun clearRouteAndDest() {
        host?.clearRoute()
        (host as? com.hellboy.tehrannav.MainActivity)?.clearDest()
        state = state.copy(destName = "", distance = "", time = "", guidance = "")
    }

    fun toast(msg: String) {
        android.util.Log.d("TehranNav", msg)
    }
}