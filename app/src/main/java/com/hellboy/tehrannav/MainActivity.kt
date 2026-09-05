package com.hellboy.tehrannav
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.hellboy.tehrannav.data.Settings
import com.hellboy.tehrannav.nav.NavEngine
import com.hellboy.tehrannav.nav.Route
import com.hellboy.tehrannav.nav.Speaker
import com.hellboy.tehrannav.ui.TehranNavApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay



class MainActivity : ComponentActivity() {

    lateinit var settings: Settings
        private set
    lateinit var map: MapView
        private set
    lateinit var speaker: Speaker
        private set

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routePolyline: Polyline? = null
    private var destMarker: Marker? = null
    var currentRoute: Route? = null
        private set
    var navActive = false
        private set
    private var nightMode = false

    /** shared with UI */
    var uiDestName = ""
        private set
    var uiDestLat = 0.0
        private set
    var uiDestLon = 0.0
        private set
    var distToDest = 0.0
        private set
    var navGuidance = ""
        private set

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        speaker = Speaker(this)

        setContent {
            TehranNavApp(activity = this)
        }
    }

    override fun onResume() = run { super.onResume(); map?.onResume() }
    override fun onPause() = run { super.onPause(); map?.onPause() }
    override fun onDestroy() = run { super.onDestroy(); speaker.shutdown(); map?.onDetach() }

    fun ensurePermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun createMap(): MapView {
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            multiTouchControls = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(12.5)
            controller.setCenter(GeoPoint(35.6892, 51.3890))
        }

        map.overlays.add(CompassOverlay(this, InternalCompassOrientationProvider(this), map).apply { enableCompass() })
        map.overlays.add(ScaleBarOverlay(map))

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map).apply {
            map.overlays.add(this)
            if (hasLocation()) {
                enableMyLocation()
                enableFollowLocation()
            }
        }
        if (!hasLocation()) ensurePermissions()
        return map
    }

    fun toggleNight() {
        nightMode = !nightMode
        map.setTileSource(if (nightMode) TileSourceFactory.MAPNIK_NIGHT else TileSourceFactory.MAPNIK)
    }

    fun nightOn() = nightMode

    fun centerOnMyLocation() {
        if (!hasLocation()) { ensurePermissions(); return }
        val loc = myLocationOverlay?.myLocation
        if (loc != null) map.controller.animateTo(loc, 17.0, 800L)
    }

    fun setDest(name: String, lat: Double, lon: Double) {
        uiDestName = name
        uiDestLat = lat
        uiDestLon = lon
    }

    fun goTo(lat: Double, lon: Double) {
        map.controller.animateTo(GeoPoint(lat, lon), 15.0, 1200L)
    }

    fun currentLocation(): GeoPoint? = myLocationOverlay?.myLocation

    fun drawRoute(route: Route) {
        routePolyline?.remove(map)
        routePolyline = Polyline(map).apply {
            setPoints(route.points.map { GeoPoint(it.first, it.second) })
            outlinePaint.color = 0xFF2979FF.toInt()
            outlinePaint.strokeWidth = 12f
        }
        map.overlays.add(routePolyline)
    }

    fun zoomToRoute(route: Route) {
        if (route.points.isEmpty()) return
        val bbox = org.osmdroid.util.BoundingBox.fromGeoPoints(
            route.points.map { GeoPoint(it.first, it.second) }
        )
        map.zoomToBoundingBox(bbox, true, 64)
    }

    fun placeDestMarker(lat: Double, lon: Double, name: String) {
        destMarker?.remove(map)
        destMarker = Marker(map).apply {
            position = GeoPoint(lat, lon)
            title = name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation)
            map.overlays.add(this)
        }
    }

    fun clearRoute() {
        routePolyline?.remove(map)
        routePolyline = null
        currentRoute = null
        destMarker?.remove(map)
        destMarker = null
        navActive = false
        distToDest = 0.0
        navGuidance = ""
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun clearDest() {
        uiDestName = ""
        uiDestLat = 0.0
        uiDestLon = 0.0
    }

    fun startNav() {
        if (currentRoute == null) return
        navActive = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lastSpokenStep = -1
        val first = currentRoute?.steps?.firstOrNull()?.instruction
            ?: "به سمت مقصد حرکت کنید"
        speaker.speak(first, settings.ttsEnabled)
        // foreground service keeps nav alive in background
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(
                Intent(this, NavService::class.java)
            )
        } else {
            startService(Intent(this, NavService::class.java))
        }
    }

    fun stopNav() {
        navActive = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopService(Intent(this, NavService::class.java))
        speaker.stop()
    }

    private var lastSpokenStep = -1

    fun speak(text: String) {
        speaker.speak(text, settings.ttsEnabled)
    }

    fun speakNext() {
        val route = currentRoute ?: return
        val loc = myLocationOverlay?.myLocation ?: return
        val idx = NavEngine.nextStepIndex(route, loc.latitude, loc.longitude)
        if (idx < route.steps.size) {
            speaker.speak(route.steps[idx].instruction, settings.ttsEnabled)
        }
    }

    // called by UI ticker every 2s during navigation
    fun navTick() {
        val route = currentRoute ?: return
        val loc = myLocationOverlay?.myLocation ?: return
        val lat = loc.latitude
        val lon = loc.longitude

        val rem = NavEngine.remainingDistance(route, lat, lon)
        distToDest = rem

        // reroute if user is far off the route (＞250m from its first point while remaining is large)
        val startDist = NavEngine.distanceMeters(lat, lon, route.points.first().first, route.points.first().second)
        if (rem > 300 && startDist > 250) {
            reroute()
            return
        }

        val idx = NavEngine.nextStepIndex(route, lat, lon)
        if (idx != lastSpokenStep && idx < route.steps.size) {
            lastSpokenStep = idx
            val step = route.steps[idx]
            val d = NavEngine.distanceMeters(lat, lon, step.locationStart.first, step.locationStart.second)
            val msg = if (d < 60) step.instruction else "پس از ${NavEngine.formatDistance(d)}، ${step.instruction}"
            navGuidance = msg
            speaker.speak(msg, settings.ttsEnabled)
        }

        if (rem < 40) {
            val msg = "به مقصد رسیدید. ${uiDestName}"
            navGuidance = msg
            speaker.speak(msg, settings.ttsEnabled)
            stopNav()
        }
    }

    private fun reroute() {
        val destLat = uiDestLat
        val destLon = uiDestLon
        if (destLat == 0.0 && destLon == 0.0) return
        val from = myLocationOverlay?.myLocation ?: return
        CoroutineScope(Dispatchers.Main).launch {
            NavEngine.route(from.latitude, from.longitude, destLat, destLon).onSuccess { route ->
                currentRoute = route
                drawRoute(route)
                navGuidance = "مسیر مجدد محاسبه شد: ${NavEngine.formatDistance(route.totalDistance)}"
                speaker.speak("مسیر مجدد محاسبه شد", settings.ttsEnabled)
            }
        }
    }
}