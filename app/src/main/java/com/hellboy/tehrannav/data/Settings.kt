package com.hellboy.tehrannav.data

import android.content.Context

/** SharedPreferences persistence: AI key, saved places, settings */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("tehran_nav", Context.MODE_PRIVATE)

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) { prefs.edit().putBoolean("tts_enabled", value).apply() }

    var currentPlaceId: Long
        get() = prefs.getLong("current_place_id", -1L)
        set(value) { prefs.edit().putLong("current_place_id", value).apply() }

    var savedRouteName: String
        get() = prefs.getString("saved_route_name", "").orEmpty()
        set(value) { prefs.edit().putString("saved_route_name", value).apply() }

    var savedRouteLat: Double
        get() = prefs.getFloat("saved_route_lat", 0f).toDouble()
        set(value) { prefs.edit().putFloat("saved_route_lat", value.toFloat()).apply() }

    var savedRouteLon: Double
        get() = prefs.getFloat("saved_route_lon", 0f).toDouble()
        set(value) { prefs.edit().putFloat("saved_route_lon", value.toFloat()).apply() }

    fun userSetWaypoint() = savedRouteLat != 0.0 || savedRouteLon != 0.0
}