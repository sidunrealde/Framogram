package com.opezee.framogram.render

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import com.opezee.framogram.config.ScreenGeometry
import com.opezee.framogram.model.ModelLoader
import com.opezee.framogram.tracking.EyeState
import java.nio.ByteBuffer

/** Snapshot of live numbers for the debug overlay (published at ~5 Hz). */
data class DebugStats(
    val renderFps: Double = 0.0,
    val trackingFps: Double = 0.0,
    val eyeX: Double = 0.0,
    val eyeY: Double = 0.0,
    val eyeZ: Double = EyeState.DEFAULT_EYE_Z,
    val tracking: Boolean = false,
    val sampleAgeMs: Double = 0.0,
    val rawMatrixDistM: Double = 0.0,
    val rawIpdDistM: Double = 0.0,
)

/**
 * The per-frame orchestrator: Choreographer loop on the main thread that pumps async
 * model loading, filters/predicts the eye position, injects the off-axis projection
 * and renders. Owns the Filament context, the grid box and the model loader.
 */
class HologramRenderer(
    surfaceView: SurfaceView,
    context: Context,
    private val eyeState: EyeState,
    initialGeometry: ScreenGeometry,
    private val trackingFpsProvider: () -> Double,
    private val onDebugStats: (DebugStats) -> Unit,
) {
    @Volatile
    var geometry: ScreenGeometry = initialGeometry
        private set

    private val filament = FilamentContext(surfaceView) { _, _ -> }
    val engine get() = filament.engine

    private val gridBox = GridBox(filament.engine, filament.scene)
    private val modelLoader = ModelLoader(filament.engine, filament.scene)

    private val choreographer = Choreographer.getInstance()
    private val projection = ProjectionCalculator.Result()
    private var running = false

    // Render FPS accounting.
    private var fpsWindowStartNs = 0L
    private var frames = 0
    private var renderFps = 0.0
    private var lastStatsPushNs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)

            val geom = geometry
            modelLoader.pump(geom)

            val eye = eyeState.eyeForFrame(frameTimeNanos)
            ProjectionCalculator.offAxis(eye[0], eye[1], eye[2], geom, projection)
            filament.camera.setCustomProjection(projection.matrix, projection.near, projection.far)
            // View orientation stays screen-aligned; only the eye translation moves.
            filament.camera.lookAt(
                eye[0], eye[1], eye[2],
                eye[0], eye[1], eye[2] - 1.0,
                0.0, 1.0, 0.0,
            )

            if (filament.render(frameTimeNanos)) frames++

            if (fpsWindowStartNs == 0L) fpsWindowStartNs = frameTimeNanos
            val windowNs = frameTimeNanos - fpsWindowStartNs
            if (windowNs > 1_000_000_000L) {
                renderFps = frames * 1e9 / windowNs
                fpsWindowStartNs = frameTimeNanos
                frames = 0
            }
            if (frameTimeNanos - lastStatsPushNs > 200_000_000L) {
                lastStatsPushNs = frameTimeNanos
                val s = eyeState.latest
                onDebugStats(
                    DebugStats(
                        renderFps = renderFps,
                        trackingFps = trackingFpsProvider(),
                        eyeX = eye[0], eyeY = eye[1], eyeZ = eye[2],
                        tracking = eyeState.isTracking,
                        sampleAgeMs = eyeState.lastSampleAgeMs.coerceAtMost(9999.0),
                        rawMatrixDistM = s?.rawMatrixDist ?: 0.0,
                        rawIpdDistM = s?.rawIpdDist ?: 0.0,
                    )
                )
            }
        }
    }

    private val lighting = Lighting(filament.engine, filament.scene, context)

    init {
        gridBox.rebuild(initialGeometry)
    }

    fun start() {
        if (running) return
        running = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /** Rotation happened: swap box dimensions, rebuild the grid, re-fit the model. */
    fun onGeometryChanged(geom: ScreenGeometry) {
        geometry = geom
        gridBox.rebuild(geom)
        modelLoader.applyAutoFit(geom)
    }

    fun loadModel(buffer: ByteBuffer): Boolean = modelLoader.startLoad(buffer)

    fun setGridPattern(pattern: Int) = gridBox.setPattern(pattern)

    fun setGridColor(r: Float, g: Float, b: Float) = gridBox.setColor(r, g, b)

    /** Lit = IBL + key light; unlit = flat shadeless look. */
    fun setLit(lit: Boolean) = lighting.setLit(lit)

    fun destroy() {
        stop()
        modelLoader.destroy()
        gridBox.destroy()
        lighting.destroy()
        filament.destroy()
    }
}
