package com.example.tennisscorer.tracking

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class BallDetector(
    context: Context,
    private val onDetections: (List<Detection>) -> Unit
) : FrameAnalyzer {

    private val objectDetector: ObjectDetector = ObjectDetector.createFromFileAndOptions(
        context,
        MODEL_FILE,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(SCORE_THRESHOLD)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        image.close()
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = objectDetector.detect(tensorImage)
        val detections = results
            .filter { result -> result.categories.any { it.label == BALL_LABEL } }
            .map { result ->
                val box = result.boundingBox
                val bestCategory = result.categories.first { it.label == BALL_LABEL }
                Detection(
                    boundingBox = RectF(
                        box.left / bitmap.width,
                        box.top / bitmap.height,
                        box.right / bitmap.width,
                        box.bottom / bitmap.height
                    ),
                    confidence = bestCategory.score
                )
            }
        onDetections(detections)
    }

    fun close() {
        objectDetector.close()
    }

    companion object {
        private const val MODEL_FILE = "efficientdet_lite0.tflite"
        private const val SCORE_THRESHOLD = 0.4f
        private const val MAX_RESULTS = 5
        private const val BALL_LABEL = "sports ball"
    }
}
