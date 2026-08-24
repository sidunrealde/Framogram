package com.opezee.framogram.model

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.opezee.framogram.config.ScreenGeometry
import java.nio.ByteBuffer

/**
 * Owns the gltfio loaders for the app's life and one loaded asset at a time.
 * Loading is asynchronous: [startLoad] begins, the render loop calls [pump] each frame,
 * and the asset is added to the scene (auto-fitted) when resource loading completes.
 * Main thread only.
 */
class ModelLoader(private val engine: Engine, private val scene: Scene) {

    companion object {
        private const val TAG = "ModelLoader"
    }

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    private var asset: FilamentAsset? = null
    private var addedToScene = false
    private var loading = false

    /** Replaces the current model with the .glb in [buffer]. Returns false on parse failure. */
    fun startLoad(buffer: ByteBuffer): Boolean {
        destroyCurrent()
        val a = assetLoader.createAsset(buffer)
        if (a == null) {
            Log.e(TAG, "createAsset failed — not a valid glTF binary?")
            return false
        }
        asset = a
        addedToScene = false
        loading = true
        resourceLoader.asyncBeginLoad(a)
        return true
    }

    /** Called every frame from the render loop. */
    fun pump(geom: ScreenGeometry) {
        if (!loading) return
        resourceLoader.asyncUpdateLoad()
        val a = asset ?: return
        if (!addedToScene && resourceLoader.asyncGetLoadProgress() >= 1f) {
            AutoFit.fit(engine, a, geom)
            scene.addEntities(a.entities)
            a.releaseSourceData()
            addedToScene = true
            loading = false
        }
    }

    /** Re-applies auto-fit (after rotation changes the box dimensions). */
    fun applyAutoFit(geom: ScreenGeometry) {
        asset?.takeIf { addedToScene }?.let { AutoFit.fit(engine, it, geom) }
    }

    private fun destroyCurrent() {
        asset?.let { a ->
            resourceLoader.evictResourceData()
            scene.removeEntities(a.entities)
            assetLoader.destroyAsset(a)
        }
        asset = null
        addedToScene = false
        loading = false
    }

    fun destroy() {
        destroyCurrent()
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
    }
}
