package com.opezee.framogram.tracking

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.opezee.framogram.config.ScreenGeometry
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Maps a MediaPipe face landmark result (in the upright, UNMIRRORED analysis image)
 * to an eye position in screen space (meters, screen-center origin).
 *
 * Coordinate handling, all in one place:
 *  - lateral x/y come from the iris-center landmarks unprojected with the real camera
 *    intrinsics (the facial transformation matrix assumes a canonical FOV, so its
 *    lateral terms are systematically off for the actual lens);
 *  - distance comes from the transformation matrix translation (≈ cm) scaled by the
 *    one-point calibration factor, with an IPD-based estimate as fallback;
 *  - the front camera is unmirrored in ImageAnalysis, so the single `x = −x_cam`
 *    negation here is the ONLY mirror handling in the app;
 *  - the camera-to-screen-center offset comes from ScreenGeometry (already rotated
 *    into the current upright frame).
 */
object EyeSpaceMapper {

    private const val IRIS_LEFT = 468
    private const val IRIS_RIGHT = 473
    private const val MEAN_IPD_M = 0.063

    fun map(
        result: FaceLandmarkerResult,
        uprightW: Int,
        uprightH: Int,
        /** Focal length in pixels of the analysis image (square pixels assumed). */
        fPx: Float,
        kCal: Float,
        geom: ScreenGeometry,
        timestampNs: Long,
    ): EyeSample? {
        val faces = result.faceLandmarks()
        if (faces.isEmpty()) return null

        // Kiosk face selection: largest landmark bounding box ≈ closest viewer.
        // Frame-to-frame identity is not tracked; the One Euro filter and the distance
        // continuity absorb occasional switches.
        var faceIdx = 0
        var bestArea = -1f
        for (i in faces.indices) {
            val a = bboxArea(faces[i])
            if (a > bestArea) {
                bestArea = a; faceIdx = i
            }
        }
        val lm = faces[faceIdx]
        if (lm.size <= IRIS_RIGHT) return null

        val li = lm[IRIS_LEFT]
        val ri = lm[IRIS_RIGHT]
        val u = (li.x() + ri.x()) / 2f
        val v = (li.y() + ri.y()) / 2f

        // IPD-based distance (also the fallback when the matrix is unavailable).
        val dxPx = (li.x() - ri.x()) * uprightW
        val dyPx = (li.y() - ri.y()) * uprightH
        val ipdPx = sqrt(dxPx * dxPx + dyPx * dyPx)
        val distIpd = if (ipdPx > 1f) fPx * MEAN_IPD_M / ipdPx else Double.MAX_VALUE.toFloat()

        // Matrix-based distance. The Tasks API documents the matrix as row-major with
        // canonical-face units of ~centimeters and the camera looking down −Z, which
        // puts tz at flat index 11; some releases have shipped it column-major (tz at
        // 14). Accept whichever candidate is physically plausible instead of trusting
        // the convention — the debug overlay shows both estimators for verification.
        var distMatrix = -1.0
        val matrixes = result.facialTransformationMatrixes()
        if (matrixes.isPresent && matrixes.get().size > faceIdx) {
            val m = matrixes.get()[faceIdx]
            if (m.size >= 16) {
                for (cand in floatArrayOf(m[11], m[14])) {
                    val d = abs(cand) / 100.0 // cm → m
                    if (d in 0.05..3.0) {
                        distMatrix = d
                        break
                    }
                }
            }
        }

        val distIpdD = distIpd.toDouble()
        val dist = when {
            distMatrix > 0 -> distMatrix * kCal
            distIpdD in 0.05..3.0 -> distIpdD
            else -> return null
        }

        // Unproject the iris midpoint with the real intrinsics; v grows downward.
        val xCam = (u - 0.5) * uprightW / fPx * dist
        val yCam = (0.5 - v) * uprightH / fPx * dist

        // Mirror: viewer's right = image left ⇒ negate x. (The one and only negation.)
        val xs = -xCam + geom.camOffsetX
        val ys = yCam + geom.camOffsetY
        return EyeSample(xs, ys, dist, timestampNs, distMatrix, distIpdD)
    }

    private fun bboxArea(lm: List<NormalizedLandmark>): Float {
        var minX = 1f; var maxX = 0f; var minY = 1f; var maxY = 0f
        for (p in lm) {
            val x = p.x(); val y = p.y()
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return (maxX - minX).coerceAtLeast(0f) * (maxY - minY).coerceAtLeast(0f)
    }
}
