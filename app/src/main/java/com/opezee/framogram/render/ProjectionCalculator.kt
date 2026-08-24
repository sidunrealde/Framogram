package com.opezee.framogram.render

import com.opezee.framogram.config.ScreenGeometry

/**
 * Kooima-style generalized off-axis projection, specialized to our fixed convention:
 * the screen plane is z = 0 with world axes equal to screen axes, so the screen basis
 * rotation is the identity and only an asymmetric frustum plus a translated view remain.
 *
 * Pure math — unit-testable without Filament.
 */
object ProjectionCalculator {

    class Result {
        /** Column-major 4x4 OpenGL projection matrix. */
        val matrix = DoubleArray(16)
        var near = 0.05
        var far = 10.0
    }

    /**
     * Computes the asymmetric frustum for an eye at (ex, ey, ez) meters in screen space
     * (ez > 0, toward the viewer) looking into a box of [geom.widthM] x [geom.heightM]
     * x [geom.depthM] behind the screen plane.
     */
    fun offAxis(ex: Double, ey: Double, ez: Double, geom: ScreenGeometry, out: Result = Result()): Result {
        val halfW = geom.widthM / 2.0
        val halfH = geom.heightM / 2.0
        // Eye clamped away from the screen plane so the math never blows up.
        val z = if (ez < 0.15) 0.15 else ez

        // Near plane scales with distance but stays well in front of the box; frustum
        // extents scale by n/z so the choice of n is transparent to the image.
        val n = (0.25 * z).coerceIn(0.02, 0.15)
        val f = z + geom.depthM + 2.0

        val l = n * (-halfW - ex) / z
        val r = n * (halfW - ex) / z
        val b = n * (-halfH - ey) / z
        val t = n * (halfH - ey) / z

        val m = out.matrix
        java.util.Arrays.fill(m, 0.0)
        m[0] = 2.0 * n / (r - l)
        m[5] = 2.0 * n / (t - b)
        m[8] = (r + l) / (r - l)
        m[9] = (t + b) / (t - b)
        m[10] = -(f + n) / (f - n)
        m[11] = -1.0
        m[14] = -2.0 * f * n / (f - n)

        out.near = n
        out.far = f
        return out
    }
}
