package com.hellboy.tehrannav.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hellboy.tehrannav.ai.GeminiClient
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
    val aiQuery: String = "",
    val aiLoading: Boolean = false,
    val aiResult: String = "",
    val aiInstructions: List<String> = emptyList(),
    val aiSummary: String = "",
    val destName: String = "",
    val destLat: Double = 0.0,
    val destLon: Double = 0.0,
    val distance: String = "",
    val time: String = "",
    val guidance: String = "",
    val showSettings: Boolean = false
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

    fun setShowSettings(v: Boolean) {
        state = state.copy(showSettings = v)
    }

    fun onAiType(q: String) {
        state = state.copy(aiQuery = q)
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
            state = state.copy(searching = true, aiLoading = false, aiResult = "")
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
                    if (GeminiClient.isConfigured(settings)) {
                        aiAdvice(name, route)
                    }
                },
                onFailure = {
                    state = state.copy(searching = false, guidance = "خطا در محاسبه مسیر: ${it.message}")
                }
            )
        }
    }

    private fun aiAdvice(destName: String, route: Route) {
        viewModelScope.launch {
            state = state.copy(aiLoading = true)
            val advice = GeminiClient.routeAdvice(destName, routeText(route), settings)
            state = state.copy(aiLoading = false)
            if (advice != null) {
                state = state.copy(
                    aiSummary = advice.summary,
                    aiInstructions = advice.directions,
                    aiResult = buildString {
                        append(advice.summary)
                        advice.directions.forEach { append("\n• ").append(it) }
                    }
                )
                host?.speak(advice.summary)
            }
        }
    }

    private fun routeText(route: Route): String {
        val sb = StringBuilder()
        sb.append("مسیر کل: ${NavEngine.formatDistance(route.totalDistance)}، مدت: ${NavEngine.formatDuration(route.totalDuration)}\n")
        route.steps.forEach { sb.append("- ").append(it.instruction).append(" («").append(it.street).append(") ").append(NavEngine.formatDistance(it.distance)).append("\n") }
        return sb.toString()
    }

    // ---------- AI natural language ----------
    fun sendAiCommand(text: String) {
        if (text.isBlank()) return
        state = state.copy(aiQuery = "", aiLoading = true, aiResult = "")
        viewModelScope.launch {
            val dest = if (GeminiClient.isConfigured(settings)) {
                GeminiClient.parseDestination(text, settings)
            } else {
                NavEngine.parseDestinationFallback(text)
            }
            if (dest.isBlank()) {
                state = state.copy(
                    aiLoading = false,
                    aiResult = "مقصدی در جمله پیدا نشد. مثلاً: «برو میدان آزادی» یا «مسیر فرودگاه مهرآباد»"
                )
                return@launch
            }
            // geocode the destination
            val places = PlaceSearch.search(dest)
            if (places.isEmpty()) {
                state = state.copy(aiLoading = false, aiResult = "مقصد «$dest» پیدا نشد. نام دقیق‌تری بگویید.")
                return@launch
            }
            val place = places.first()
            state = state.copy(aiLoading = false)
            pickDestination(place.name, place.lat, place.lon)
        }
    }

    // ---------- AI chat with context ----------
    private var chatHistory = mutableListOf<Pair<String, String>>()

    fun aiChat(msg: String) {
        if (msg.isBlank()) return
        state = state.copy(aiQuery = "", aiLoading = true)
        chatHistory.add("user" to msg)
        viewModelScope.launch {
            val dest = GeminiClient.parseDestination(msg, settings)
            if (dest.isNotBlank()) {
                val places = PlaceSearch.search(dest)
                if (places.isNotEmpty()) {
                    val p = places.first()
                    chatHistory.add("ai" to "مقصد «${p.name}» انتخاب شد.")
                    pickDestination(p.name, p.lat, p.lon)
                    return@launch
                }
            }
            // free-form answer without routing
            val answer = GeminiClient.chat(
                msg, history = chatHistory.takeLast(20).map { it.first to it.second }, settings
            )
            chatHistory.add("ai" to answer)
            state = state.copy(aiLoading = false, aiResult = answer)
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
        state = state.copy(destName = "", distance = "", time = "", guidance = "", aiResult = "", aiInstructions = emptyList())
        host?.clearRoute()
    }

    fun clearRouteAndDest() {
        host?.clearRoute()
        (host as? com.hellboy.tehrannav.MainActivity)?.clearDest()
        state = state.copy(destName = "", distance = "", time = "", guidance = "", aiResult = "")
    }

    fun toast(msg: String) {
        android.util.Log.d("TehranNav", msg)
    }
}