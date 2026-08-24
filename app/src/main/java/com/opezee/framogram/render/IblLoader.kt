package com.opezee.framogram.render

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.utils.KTX1Loader
import java.nio.ByteBuffer

/**
 * Loads the prefiltered KTX environment as an IndirectLight and adds a directional
 * key light. No skybox: the void around the box stays black.
 */
object IblLoader {

    fun setupLighting(engine: Engine, scene: Scene, context: Context): Int {
        val buffer = context.assets.open("envs/default_env_ibl.ktx").use {
            ByteBuffer.wrap(it.readBytes())
        }
        val bundle = KTX1Loader.createIndirectLight(engine, buffer)
        val ibl: IndirectLight? = bundle.indirectLight
        if (ibl != null) {
            ibl.intensity = 30_000f
            scene.indirectLight = ibl
        }

        val sun = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.98f, 0.95f)
            .intensity(50_000f)
            .direction(0.2f, -0.6f, -0.77f)
            .castShadows(false)
            .build(engine, sun)
        scene.addEntity(sun)
        return sun
    }
}
