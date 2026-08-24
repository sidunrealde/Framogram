package com.opezee.framogram.render

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.utils.KTX1Loader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Scene lighting with two modes:
 *
 *  - LIT: the prefiltered KTX environment (image-based lighting) plus a directional
 *    key light — the showcase look.
 *  - UNLIT: a flat, shadeless look — a uniform white irradiance (1-band spherical
 *    harmonics) paired with a 1x1 white reflections cubemap so metals stay visible
 *    instead of going black, and no directional light.
 */
class Lighting(private val engine: Engine, private val scene: Scene, context: Context) {

    private val litIbl: IndirectLight?
    private val flatIbl: IndirectLight
    private val flatCubemap: Texture
    private val sun: Int
    // Declared before init: the init block calls setLit(), which writes this flag.
    private var sunInScene = false

    init {
        val buffer = context.assets.open("envs/default_env_ibl.ktx").use {
            ByteBuffer.wrap(it.readBytes())
        }
        litIbl = KTX1Loader.createIndirectLight(engine, buffer).indirectLight?.apply {
            intensity = 30_000f
        }

        // 1x1 white cubemap: 6 faces of a single RGBA pixel (depth selects the face).
        flatCubemap = Texture.Builder()
            .width(1).height(1).levels(1)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.RGBA8)
            .build(engine)
        val pixels = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        repeat(6 * 4) { pixels.put(0xFF.toByte()) }
        pixels.rewind()
        flatCubemap.setImage(
            engine, 0, 0, 0, 0, 1, 1, 6,
            Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE),
        )
        flatIbl = IndirectLight.Builder()
            .reflections(flatCubemap)
            // 1-band SH: sh[0] is the average irradiance (pre-scaled convention).
            .irradiance(1, floatArrayOf(1f, 1f, 1f))
            .intensity(25_000f)
            .build(engine)

        sun = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.98f, 0.95f)
            .intensity(50_000f)
            .direction(0.2f, -0.6f, -0.77f)
            .castShadows(false)
            .build(engine, sun)

        setLit(true)
    }

    fun setLit(lit: Boolean) {
        if (lit) {
            scene.indirectLight = litIbl ?: flatIbl
            if (!sunInScene) {
                scene.addEntity(sun)
                sunInScene = true
            }
        } else {
            scene.indirectLight = flatIbl
            if (sunInScene) {
                scene.removeEntity(sun)
                sunInScene = false
            }
        }
    }

    fun destroy() {
        scene.indirectLight = null
        if (sunInScene) scene.removeEntity(sun)
        engine.lightManager.destroy(sun)
        EntityManager.get().destroy(sun)
        litIbl?.let { engine.destroyIndirectLight(it) }
        engine.destroyIndirectLight(flatIbl)
        engine.destroyTexture(flatCubemap)
    }
}
