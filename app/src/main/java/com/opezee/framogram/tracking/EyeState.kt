package com.opezee.framogram.tracking

import kotlin.math.exp

/**
 * A single eye-position measurement in screen space (meters, screen-center origin,
 * +X right / +Y up / +Z toward the viewer). Produced on the MediaPipe callback thread,
 * consumed on the render thread via a volatile swap — only the newest sample matters.
 */
data class EyeSample(
    val x: Double,
    val y: Double,
    val z: Double,
    val timestampNs: Long,
    /** Raw distance from the facial transformation matrix, meters (pre-calibration). */
    val rawMatrixDist: Double,
    /** Raw distance from the IPD estimator, meters. */
    val rawIpdDist: Double,
)

/**
 * Render-thread eye state: One Euro smoothing, forward prediction by the measured
 * pipeline latency, and easing back to a default eye when the face is lost.
 */
class EyeState {

    companion object {
        const val DEFAULT_EYE_Z = 0.65
        private const val LOST_AFTER_NS = 400_000_000L      // 400 ms without a sample
        private const val EASE_TAU_S = 0.4
        private const val MAX_PREDICT_S = 0.08              // clamp latency compensation
    }

    @Volatile
    var latest: EyeSample? = null

    /** Timestamp of the newest sample, for the debug overlay's tracking-age metric. */
    val lastSampleAgeMs: Double
        get() = latest?.let { (System.nanoTime() - it.timestampNs) / 1e6 } ?: Double.MAX_VALUE

    private val fx = OneEuroFilter()
    private val fy = OneEuroFilter()
    private val fz = OneEuroFilter(beta = 0.3)

    private var lastConsumedTs = 0L
    private var wasTracking = false
    private var lastFrameNs = 0L

    // Current output position (also the easing state when the face is lost).
    private var outX = 0.0
    private var outY = 0.0
    private var outZ = DEFAULT_EYE_Z

    var isTracking = false
        private set

    /**
     * Returns the eye position to render this frame: filtered + predicted while tracking,
     * eased toward the default eye while the face is lost.
     */
    fun eyeForFrame(frameTimeNanos: Long): DoubleArray {
        val sample = latest
        val dtFrame = if (lastFrameNs == 0L) 0.0 else (frameTimeNanos - lastFrameNs) / 1e9
        lastFrameNs = frameTimeNanos

        val fresh = sample != null && frameTimeNanos - sample.timestampNs < LOST_AFTER_NS
        isTracking = fresh

        if (fresh && sample != null) {
            if (!wasTracking) {
                // Reacquired: seed the filters at the current eased position, no jump-cut.
                val t = sample.timestampNs / 1e9
                fx.resetTo(outX, t); fy.resetTo(outY, t); fz.resetTo(outZ, t)
                wasTracking = true
            }
            if (sample.timestampNs != lastConsumedTs) {
                lastConsumedTs = sample.timestampNs
                val t = sample.timestampNs / 1e9
                outX = fx.filter(sample.x, t)
                outY = fy.filter(sample.y, t)
                outZ = fz.filter(sample.z, t)
            }
            // Predict forward by the measured motion-to-render latency.
            val lat = ((frameTimeNanos - sample.timestampNs) / 1e9).coerceIn(0.0, MAX_PREDICT_S)
            return doubleArrayOf(
                outX + fx.velocity * lat,
                outY + fy.velocity * lat,
                (outZ + fz.velocity * lat).coerceAtLeast(0.15),
            )
        }

        // Face lost: exponential ease toward the default centered eye.
        wasTracking = false
        if (dtFrame > 0) {
            val k = 1.0 - exp(-dtFrame / EASE_TAU_S)
            outX += (0.0 - outX) * k
            outY += (0.0 - outY) * k
            outZ += (DEFAULT_EYE_Z - outZ) * k
        }
        return doubleArrayOf(outX, outY, outZ)
    }
}
