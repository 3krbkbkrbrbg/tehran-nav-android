package com.hellboy.tehrannav

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

/** Downloads OSM tiles for a region into the osmdroid cache (offline use). */
class OfflineMap(
    private val ctx: Context,
    private val map: MapView
) {
    interface Listener {
        fun onProgress(done: Int, total: Int)
        fun onFinished(success: Boolean, count: Int)
    }

    fun downloadRegion(bbox: BoundingBox, minZ: Int, maxZ: Int, listener: Listener?) {
        Thread {
            var count = 0
            try {
                MapTileProviderBasic(ctx)
                val manager = CacheManager(map)
                var total = 0
                for (z in minZ..maxZ) {
                    val n = Math.pow(2.0, z.toDouble())
                    val north = ((1.0 - Math.log(Math.tan(Math.toRadians(bbox.latNorth)) + 1.0 / Math.cos(Math.toRadians(bbox.latNorth))) / Math.PI) / 2.0 * n).toInt()
                    val south = ((1.0 - Math.log(Math.tan(Math.toRadians(bbox.latSouth)) + 1.0 / Math.cos(Math.toRadians(bbox.latSouth))) / Math.PI) / 2.0 * n).toInt()
                    val west = ((bbox.lonWest + 180.0) / 360.0 * n).toInt()
                    val east = ((bbox.lonEast + 180.0) / 360.0 * n).toInt()
                    total += ((east - west + 1) * (north - south + 1)).toInt()
                }
                var failed = false
                manager.downloadAreaAsync(
                    ctx, bbox, minZ, maxZ,
                    object : CacheManager.CacheManagerCallback {
                        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int): Boolean {
                            listener?.onProgress(progress, total)
                            return true
                        }
                        override fun onTaskComplete(): Boolean {
                            listener?.onFinished(!failed, count)
                            return true
                        }
                        override fun onTaskFailed(): Boolean {
                            failed = true
                            listener?.onFinished(false, count)
                            return false
                        }
                    }
                )
            } catch (e: Exception) {
                listener?.onFinished(false, count)
            }
        }.start()
    }
}