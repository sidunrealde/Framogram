package com.opezee.framogram.model

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.opezee.framogram.config.ScreenGeometry

/**
 * Normalizes a loaded asset's root transform so ANY model — mm-scale, km-scale,
 * off-center origin — appears centered (x/y) inside the hologram box at a consistent
 * size (60% of the smallest box dimension), with its FRONT face just behind the screen
 * plane. Content at the screen plane has zero parallax, so anchoring the front there
 * keeps the model visually stable while the head moves; only its depth recedes.
 */
object AutoFit {

    private const val FILL = 0.6f

    /** Small gap behind the glass so the model never pokes through the screen plane. */
    private const val FRONT_GAP_M = 0.005f

    fun fit(engine: Engine, asset: FilamentAsset, geom: ScreenGeometry) {
        val box = asset.boundingBox
        val center = box.center
        val he = box.halfExtent
        val maxExtent = 2f * maxOf(he[0], he[1], he[2])
        if (maxExtent <= 0f) return

        val scale = FILL * minOf(geom.widthM, geom.heightM, geom.depthM) / maxExtent
        val tx = 0f - scale * center[0]
        val ty = 0f - scale * center[1]
        // Front of the scaled bounding box lands at z = -FRONT_GAP_M.
        val tz = -FRONT_GAP_M - scale * (center[2] + he[2])

        // Column-major: uniform scale + translation.
        val m = floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            tx, ty, tz, 1f,
        )
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(asset.root), m)
    }
}
