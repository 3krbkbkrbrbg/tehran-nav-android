package com.hellboy.tehrannav

import android.content.Context
import android.os.AsyncTask
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Downloads OSM tiles for a region (Iran or current viewport) into the tile
 * cache so the map works fully offline afterwards.
 */
class OfflineMap(private val context: Context, private val map: MapView) {

    interface Listener {
        fun onProgress(done: Int, total: Int)
        fun onFinished(success: Boolean, count: Int)
    }

    fun downloadRegion(bbox: BoundingBox, zoomFrom: Int = 5, zoomTo: Int = 15, listener: Listener?) {
        // do work on background thread
        Thread {
            val provider = MapTileProviderBasic(context)
            val cache = provider.tileCache
            var done = 0
            var total = 0
            // count all tiles first
            for (z in zoomFrom..zoomTo) {
                val tlr = tileRange(bbox, z)
                total += ((tlr[1] - tlr[0] + 1) * (tlr[3] - tlr[2] + 1))
            }
            for (z in zoomFrom..zoomTo) {
                val tlr = tileRange(bbox, z)
                for (x in tlr[0]..tlr[1]) {
                    for (y in tlr[2]..tlr[3]) {
                        val tile = provider.getTile(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK, x, y, z, null)
                        cache.saveFile(tile, tile.expirationTime)
                        done++
                        listener?.onProgress(done, total)
                        Thread.sleep(10) // be polite to the tile server
                    }
                }
            }
            provider.detach()
            listener?.onFinished(true, done)
        }.start()
    }

    private fun tileRange(bbox: BoundingBox, zoom: Int): IntArray {
        // north, south, east, west
        val n = latToTileY(bbox.latNorth, zoom)
        val s = latToTileY(bbox.latSouth, zoom)
        val e = lonToTileX(bbox.lonEast, zoom)
        val w = lonToTileX(bbox.lonWest, zoom)
        return intArrayOf(min(w, e), max(w, e), min(n, s), max(n, s))
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        val n = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0
        return (n * (1 shl zoom)).toInt()
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }
}