package com.opezee.framogram.model

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.opezee.framogram.config.ScreenGeometry

/**
 * Normalizes a loaded asset's root transform so ANY model — mm-scale, km-scale,
 * off-center origin — appears centered inside the hologram box at a consistent size
 * (60% of the smallest box dimension), placed at mid-depth behind the screen plane.
 */
object AutoFit {

    private const val FILL = 0.6f

    fun fit(engine: Engine, asset: FilamentAsset, geom: ScreenGeometry) {
        val box = asset.boundingBox
        val center = box.center
        val he = box.halfExtent
        val maxExtent = 2f * maxOf(he[0], he[1], he[2])
        if (maxExtent <= 0f) return

        val scale = FILL * minOf(geom.widthM, geom.heightM, geom.depthM) / maxExtent
        val tx = 0f - scale * center[0]
        val ty = 0f - scale * center[1]
        val tz = -geom.depthM / 2f - scale * center[2]

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
