package com.example.objectcounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

/** One detected object, with its box in the coordinates of the analysed image. */
data class Det(val box: RectF, val label: String, val score: Float)

/** Everything the detector found in a single image / frame. */
data class DetectionResult(val detections: List<Det>, val imageWidth: Int, val imageHeight: Int)

/**
 * Wraps the MediaPipe ObjectDetector (EfficientDet-Lite0, 80 COCO classes).
 * Two detectors are kept: one for live camera frames and one for still photos.
 * Results are returned with a low score threshold; the UI filters by the slider.
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val onLiveResult: (DetectionResult) -> Unit,
    private val onError: (String) -> Unit
) {
    private var liveDetector: ObjectDetector? = null
    private var stillDetector: ObjectDetector? = null

    private fun options(mode: RunningMode): ObjectDetector.ObjectDetectorOptions {
        val base = BaseOptions.builder().setModelAssetPath(MODEL_FILE).build()
        val builder = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(mode)
            .setMaxResults(100)
            .setScoreThreshold(MIN_SCORE)
        if (mode == RunningMode.LIVE_STREAM) {
            builder.setResultListener { result: ObjectDetectorResult, input ->
                onLiveResult(toResult(result, input.width, input.height))
            }
            builder.setErrorListener { e -> onError(e.message ?: "Detection error") }
        }
        return builder.build()
    }

    fun setupLive() {
        if (liveDetector != null) return
        try {
            liveDetector = ObjectDetector.createFromOptions(context, options(RunningMode.LIVE_STREAM))
        } catch (e: Exception) {
            onError("Could not load model: ${e.message}")
        }
    }

    private fun setupStill() {
        if (stillDetector != null) return
        try {
            stillDetector = ObjectDetector.createFromOptions(context, options(RunningMode.IMAGE))
        } catch (e: Exception) {
            onError("Could not load model: ${e.message}")
        }
    }

    /** Feed one camera frame (already rotated upright). Result arrives via onLiveResult. */
    fun detectLive(bitmap: Bitmap) {
        liveDetector?.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())
    }

    /** Detect objects in a still photo. Blocking: call from a background thread. */
    fun detectStill(bitmap: Bitmap): DetectionResult? {
        setupStill()
        val detector = stillDetector ?: return null
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        return toResult(result, bitmap.width, bitmap.height)
    }

    fun close() {
        liveDetector?.close()
        stillDetector?.close()
        liveDetector = null
        stillDetector = null
    }

    private fun toResult(result: ObjectDetectorResult, w: Int, h: Int): DetectionResult {
        val list = result.detections().mapNotNull { d ->
            val top = d.categories().firstOrNull() ?: return@mapNotNull null
            Det(RectF(d.boundingBox()), top.categoryName(), top.score())
        }
        return DetectionResult(list, w, h)
    }

    companion object {
        const val MODEL_FILE = "efficientdet_lite0.tflite"
        const val MIN_SCORE = 0.2f
    }
}
