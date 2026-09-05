package com.hellboy.tehrannav

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.TileSystem
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
                    val north = TileSystem.LatitudeToTileY(bbox.latNorth, z)
                    val south = TileSystem.LatitudeToTileY(bbox.latSouth, z)
                    val west = TileSystem.LongitudeToTileX(bbox.lonWest, z)
                    val east = TileSystem.LongitudeToTileX(bbox.lonEast, z)
                    total += ((east - west + 1) * (north - south + 1)).toInt()
                }
                val done = manager.downloadAreaAsync(
                    ctx, bbox, minZ, maxZ,
                    object : CacheManager.CacheManagerCallback {
                        override fun onTileDownloaded() { count++ }
                        override fun onDownloadFailed() {}
                        override fun onTaskComplete() {}
                        override fun onTaskFailed() {}
                        override fun updateProgress(p: Int, q: Int) {
                            listener?.onProgress(p, q)
                        }
                    }
                )
                var tries = 0
                while (!done.isDone && tries < 1200) {
                    Thread.sleep(1000)
                    tries++
                    listener?.onProgress(count, total)
                }
                listener?.onFinished(true, count)
            } catch (e: Exception) {
                listener?.onFinished(false, count)
            }
        }.start()
    }
}