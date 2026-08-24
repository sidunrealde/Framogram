package com.opezee.framogram.config

import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.max

/**
 * Physical geometry of the screen and the hologram box, in meters.
 *
 * World convention (fixed for the whole app): origin at the physical center of the screen glass,
 * +X right / +Y up as the viewer sees the screen, +Z out of the screen toward the viewer.
 * The hologram box occupies x ∈ [−W/2, W/2], y ∈ [−H/2, H/2], z ∈ [−D, 0].
 */
class ScreenGeometry(
    /** Physical screen width in meters, current orientation. */
    val widthM: Float,
    /** Physical screen height in meters, current orientation. */
    val heightM: Float,
    /** Hologram box depth in meters (orientation-invariant). */
    val depthM: Float,
    /** Front camera offset from screen center in the current upright frame, meters. */
    val camOffsetX: Float,
    val camOffsetY: Float,
    /** Display rotation this geometry was computed for (Surface.ROTATION_*). */
    val rotation: Int,
    val widthPx: Int,
    val heightPx: Int,
) {
    companion object {
        fun compute(context: Context, rotation: Int): ScreenGeometry {
            val dm = context.resources.displayMetrics
            val wm = context.getSystemService(WindowManager::class.java)

            // Full window bounds (immersive app draws edge to edge).
            val (curWpx, curHpx) = if (Build.VERSION.SDK_INT >= 30) {
                val b = wm.currentWindowMetrics.bounds
                b.width() to b.height()
            } else {
                val m = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(m)
                m.widthPixels to m.heightPixels
            }

            val rotated = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            // xdpi/ydpi are reported for the display's NATURAL orientation on many devices,
            // so compute physical size in natural orientation first, then swap.
            val natWpx = if (rotated) curHpx else curWpx
            val natHpx = if (rotated) curWpx else curHpx
            val natWm = natWpx / dm.xdpi * 0.0254f
            val natHm = natHpx / dm.ydpi * 0.0254f

            val widthM = if (rotated) natHm else natWm
            val heightM = if (rotated) natWm else natHm
            val depthM = max(natWm, natHm)

            val (ox, oy) = DeviceProfile.cameraOffsetNatural(natWm, natHm)
            // Re-express the natural-orientation camera offset in the current upright frame.
            // This table is verified empirically via the debug overlay (M3).
            val (cx, cy) = when (rotation) {
                Surface.ROTATION_90 -> oy to -ox
                Surface.ROTATION_180 -> -ox to -oy
                Surface.ROTATION_270 -> -oy to ox
                else -> ox to oy
            }

            return ScreenGeometry(widthM, heightM, depthM, cx, cy, rotation, curWpx, curHpx)
        }
    }
}
