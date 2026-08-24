package com.opezee.framogram.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.opezee.framogram.config.ScreenGeometry
import java.util.concurrent.Executors
import kotlin.math.tan

/**
 * Front-camera face tracking: CameraX ImageAnalysis → upright bitmap → MediaPipe
 * FaceLandmarker → [EyeSpaceMapper] → [EyeState.latest].
 *
 * Latency posture: 640x480 analysis, KEEP_ONLY_LATEST backpressure, RGBA output
 * (CameraX does the YUV conversion), single rotation copy, no queues anywhere.
 */
class FaceTracker(
    private val context: Context,
    private val eyeState: EyeState,
    /** Current screen geometry — read per result so rotation is always up to date. */
    private val geomProvider: () -> ScreenGeometry,
    /** Distance calibration factor from settings. */
    private val kCalProvider: () -> Float,
) {
    companion object {
        private const val TAG = "FaceTracker"
    }

    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var bitmapBuffer: Bitmap? = null

    @Volatile
    private var fPx = 0f

    /** Raw (uncalibrated) matrix distance of the last mapped sample, for calibration. */
    @Volatile
    var lastRawMatrixDist: Double = 0.0
        private set

    /** Rolling tracking-rate estimate, results per second. */
    @Volatile
    var trackingFps: Double = 0.0
        private set

    private var fpsWindowStartNs = 0L
    private var fpsWindowCount = 0

    private val engine = FaceLandmarkerEngine(context) { result, uprightW, uprightH ->
        val tsNs = result.timestampMs() * 1_000_000L
        val sample = EyeSpaceMapper.map(
            result, uprightW, uprightH, fPx, kCalProvider(), geomProvider(), tsNs
        )
        if (sample != null) {
            if (sample.rawMatrixDist > 0) lastRawMatrixDist = sample.rawMatrixDist
            eyeState.latest = sample
        }
        val now = System.nanoTime()
        if (fpsWindowStartNs == 0L) fpsWindowStartNs = now
        fpsWindowCount++
        if (now - fpsWindowStartNs > 1_000_000_000L) {
            trackingFps = fpsWindowCount * 1e9 / (now - fpsWindowStartNs)
            fpsWindowStartNs = now
            fpsWindowCount = 0
        }
    }

    fun bind(lifecycleOwner: LifecycleOwner, targetRotation: Int) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(targetRotation)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { image -> analyze(image) }

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis
                )
                imageAnalysis = analysis
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setTargetRotation(rotation: Int) {
        imageAnalysis?.targetRotation = rotation
    }

    fun shutdown() {
        analyzerExecutor.execute { engine.close() }
        analyzerExecutor.shutdown()
    }

    private fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val rowStridePx = plane.rowStride / 4
            val buf = bitmapBuffer?.takeIf { it.width == rowStridePx && it.height == image.height }
                ?: Bitmap.createBitmap(rowStridePx, image.height, Bitmap.Config.ARGB_8888)
                    .also { bitmapBuffer = it }
            plane.buffer.rewind()
            buf.copyPixelsFromBuffer(plane.buffer)

            if (fPx == 0f) fPx = computeFocalPx(image.width)

            val rotation = image.imageInfo.rotationDegrees
            // Crop row-stride padding and rotate to upright in one copy. The frame is
            // NOT mirrored — EyeSpaceMapper handles the front-camera mirror.
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val upright = Bitmap.createBitmap(buf, 0, 0, image.width, image.height, matrix, false)

            // uptimeMillis shares a clock base with Choreographer's frameTimeNanos, so
            // sample age and prediction math downstream stay consistent. (The sensor
            // timestamp's clock base is not guaranteed across devices.)
            engine.detect(upright, android.os.SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        } finally {
            image.close()
        }
    }

    private fun computeFocalPx(analysisWidthPx: Int): Float {
        val cam = camera ?: return fallbackFocalPx(analysisWidthPx)
        return try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val focalMm =
                info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull()
            val sensor =
                info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (focalMm != null && sensor != null && sensor.width > 0f) {
                focalMm / sensor.width * analysisWidthPx
            } else fallbackFocalPx(analysisWidthPx)
        } catch (e: Exception) {
            Log.w(TAG, "Intrinsics unavailable, using FOV fallback", e)
            fallbackFocalPx(analysisWidthPx)
        }
    }

    /** Assume a typical ~60° horizontal front-camera FOV when intrinsics are missing. */
    private fun fallbackFocalPx(analysisWidthPx: Int): Float =
        (analysisWidthPx / 2f) / tan(Math.toRadians(30.0)).toFloat()
}
