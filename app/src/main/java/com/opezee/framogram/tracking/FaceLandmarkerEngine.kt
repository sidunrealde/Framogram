package com.opezee.framogram.tracking

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Wraps the MediaPipe FaceLandmarker in LIVE_STREAM mode with a GPU delegate and a
 * one-shot fallback to CPU if the GPU delegate fails at init or at runtime.
 *
 * All calls to [detect] must come from a single thread (the CameraX analyzer executor);
 * results arrive on a MediaPipe-owned thread via [onResult].
 */
class FaceLandmarkerEngine(
    private val context: Context,
    private val onResult: (result: FaceLandmarkerResult, uprightW: Int, uprightH: Int) -> Unit,
) {
    companion object {
        private const val TAG = "FaceLandmarkerEngine"
        private const val MODEL_ASSET = "mediapipe/face_landmarker.task"
    }

    private var landmarker: FaceLandmarker? = null
    private var usingGpu = true

    @Volatile
    private var gpuFailed = false

    private fun create(delegate: Delegate): FaceLandmarker {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(delegate)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(2)
            .setOutputFacialTransformationMatrixes(true)
            .setOutputFaceBlendshapes(false)
            .setMinFaceDetectionConfidence(0.5f)
            .setResultListener { result, input ->
                onResult(result, input.width, input.height)
            }
            .setErrorListener { e ->
                Log.e(TAG, "FaceLandmarker error (gpu=$usingGpu)", e)
                if (usingGpu) gpuFailed = true
            }
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    /** [timestampMs] must be monotonically increasing (uptime-based). */
    fun detect(upright: Bitmap, timestampMs: Long) {
        if (gpuFailed && usingGpu) {
            Log.w(TAG, "GPU delegate failed; recreating FaceLandmarker on CPU")
            landmarker?.close()
            landmarker = null
            usingGpu = false
        }
        val lm = landmarker ?: try {
            create(if (usingGpu) Delegate.GPU else Delegate.CPU)
        } catch (e: Exception) {
            if (usingGpu) {
                Log.w(TAG, "GPU delegate init failed; falling back to CPU", e)
                usingGpu = false
                create(Delegate.CPU)
            } else throw e
        }.also { landmarker = it }

        lm.detectAsync(BitmapImageBuilder(upright).build(), timestampMs)
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
