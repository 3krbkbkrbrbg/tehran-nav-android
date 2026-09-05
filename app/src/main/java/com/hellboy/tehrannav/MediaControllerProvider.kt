package com.hellboy.tehrannav

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Thin holder for a MediaController connected to [PlaybackService].
 * The controller targets the same background ExoPlayer that the
 * foreground service owns, so UI controls drive background playback.
 */
object MediaControllerProvider {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    /**
     * Start building the controller (async). Call once from onCreate,
     * then pass this provider's [controller] to the UI after it's ready.
     * Returns null until build completes.
     */
    fun connect(ctx: Context): ListenableFuture<MediaController> {
        controllerFuture?.let { return it }
        val sessionToken = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            androidx.core.content.ContextCompat.getMainExecutor(ctx)
        )
        return future
    }

    fun release() {
        controller?.release()
        controller = null
        controllerFuture = null
    }
}
