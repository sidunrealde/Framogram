package com.opezee.framogram.tracking

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * One Euro filter (Casiez et al.) with the filtered derivative exposed so the renderer
 * can extrapolate the eye position forward by the pipeline latency.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.5,
    private val dCutoff: Double = 1.0,
) {
    private var initialized = false
    private var lastTimeS = 0.0
    private var xPrev = 0.0
    private var dxPrev = 0.0

    /** Filtered velocity estimate, units/second. */
    val velocity: Double get() = if (initialized) dxPrev else 0.0

    fun reset() {
        initialized = false
        dxPrev = 0.0
    }

    /** Forces the filter state to [x] with zero velocity (used after fallback easing). */
    fun resetTo(x: Double, timeS: Double) {
        initialized = true
        lastTimeS = timeS
        xPrev = x
        dxPrev = 0.0
    }

    fun filter(x: Double, timeS: Double): Double {
        if (!initialized) {
            initialized = true
            lastTimeS = timeS
            xPrev = x
            dxPrev = 0.0
            return x
        }
        val dt = (timeS - lastTimeS).coerceIn(1e-4, 0.5)
        lastTimeS = timeS

        val dx = (x - xPrev) / dt
        val adx = alpha(dCutoff, dt)
        dxPrev = adx * dx + (1 - adx) * dxPrev

        val cutoff = minCutoff + beta * abs(dxPrev)
        val a = alpha(cutoff, dt)
        xPrev = a * x + (1 - a) * xPrev
        return xPrev
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 - exp(-dt / tau)
    }
}
