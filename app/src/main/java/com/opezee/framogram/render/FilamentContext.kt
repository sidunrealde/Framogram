package com.opezee.framogram.render

import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Utils

/**
 * Owns the Filament engine, renderer, scene, view, camera and the SurfaceView plumbing.
 * All methods must be called from the main thread (Filament's backend runs on its own
 * internal thread; the Java API is single-threaded by design).
 */
class FilamentContext(
    surfaceView: SurfaceView,
    private val onResized: (width: Int, height: Int) -> Unit,
) {
    companion object {
        init {
            // Loads the filament, gltfio and filament-utils native libraries.
            Utils.init()
        }
    }

    val engine: Engine = Engine.create()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    val camera: Camera = engine.createCamera(cameraEntity)

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null

    init {
        view.scene = scene
        view.camera = camera
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply {
            enabled = true
            sampleCount = 4
        }
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        }

        uiHelper.isOpaque = true
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                this@FilamentContext.onResized(width, height)
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    /** Renders one frame; returns false if the surface is not ready. */
    fun render(frameTimeNanos: Long): Boolean {
        val sc = swapChain ?: return false
        if (!uiHelper.isReadyToRender) return false
        return if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            true
        } else false
    }

    fun destroy() {
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }
}
