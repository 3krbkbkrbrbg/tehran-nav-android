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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

data class UiState(
    val searchQuery: String = "",
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

    private var lastLoc: GeoPoint? = null

    // ---------- search ----------
    fun search(q: String) {
        state = state.copy(searchQuery = q)
        if (q.isBlank()) { state = state.copy(searchResults = emptyList()); return }
        viewModelScope.launch {
            state = state.copy(searching = true)
            val results = PlaceSearch.search(q)
            state = state.copy(searching = false, searchResults = results)
        }
    }

    fun onSearchType(q: String) {
        state = state.copy(searchQuery = q)
    }

    fun dismissResults() {
        state = state.copy(searchResults = emptyList())
    }

    fun setShowOffline(v: Boolean) {
        state = state.copy(showOffline = v)
    }

    fun setShowCoords(v: Boolean) {
        state = state.copy(showCoords = v)
    }

    // ---------- destination & routing ----------
    fun pickDestination(name: String, lat: Double, lon: Double) {
        state = state.copy(
            destName = name, destLat = lat, destLon = lon,
            searchResults = emptyList(), searchQuery = ""
        )
        (host as? com.hellboy.tehrannav.MainActivity)?.setDest(name, lat, lon)
        host?.placeDestMarker(lat, lon, name)
        host?.goTo(lat, lon)
        computeRoute(lat, lon, name)
    }

    fun computeRoute(toLat: Double, toLon: Double, name: String) {
        viewModelScope.launch {
            state = state.copy(searching = true)
            val from = host?.currentLocation() ?: GeoPoint(35.6892, 51.3890)
            NavEngine.route(from.latitude, from.longitude, toLat, toLon).fold(
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