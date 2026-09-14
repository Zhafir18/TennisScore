package com.example.tennisscorer.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloV8Detector(
    context: Context,
    private val onDetections: (List<Detection>) -> Unit
) : FrameAnalyzer {

    private val interpreter: Interpreter = run {
        val fd = context.assets.openFd(MODEL_FILE)
        val mapped = FileInputStream(fd.fileDescriptor)
            .channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        Interpreter(mapped)
    }

    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        image.close()
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(scaled)
        val output = Array(1) { Array(NUM_FEATURES) { FloatArray(NUM_ANCHORS) } }
        interpreter.run(inputBuffer, output)
        onDetections(parseOutput(output[0]))
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buf.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buf.putFloat(((pixel shr 8)  and 0xFF) / 255f)
            buf.putFloat((pixel          and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    fun close() { interpreter.close() }

    companion object {
        const val MODEL_FILE     = "yolov8_tennis_ball.tflite"
        const val CONF_THRESHOLD = 0.35f
        const val IOU_THRESHOLD  = 0.45f
        const val INPUT_SIZE     = 640
        const val NUM_ANCHORS    = 8400
        const val NUM_FEATURES   = 21  // 4 box coords + 17 classes

        // Dataset has 3 duplicate tennis-ball class names at indices 0, 14, 16
        // Feature index = 4 + class_index
        private val BALL_FEATURE_INDICES = intArrayOf(4, 18, 20)

        fun parseOutput(
            output: Array<FloatArray>,
            confThreshold: Float = CONF_THRESHOLD,
            iouThreshold: Float  = IOU_THRESHOLD
        ): List<Detection> {
            val candidates = mutableListOf<Pair<RectF, Float>>()
            for (i in 0 until NUM_ANCHORS) {
                val conf = BALL_FEATURE_INDICES.maxOf { fi -> output[fi][i] }
                if (conf < confThreshold) continue
                val cx = output[0][i]; val cy = output[1][i]
                val w  = output[2][i]; val h  = output[3][i]
                val box = RectF()
                box.left = cx - w / 2f; box.top = cy - h / 2f
                box.right = cx + w / 2f; box.bottom = cy + h / 2f
                candidates.add(Pair(box, conf))
            }
            return nms(candidates, iouThreshold).map { (box, conf) ->
                val normBox = RectF()
                normBox.left   = (box.left   / INPUT_SIZE).coerceIn(0f, 1f)
                normBox.top    = (box.top    / INPUT_SIZE).coerceIn(0f, 1f)
                normBox.right  = (box.right  / INPUT_SIZE).coerceIn(0f, 1f)
                normBox.bottom = (box.bottom / INPUT_SIZE).coerceIn(0f, 1f)
                Detection(boundingBox = normBox, confidence = conf)
            }
        }

        fun nms(
            candidates: List<Pair<RectF, Float>>,
            iouThreshold: Float = IOU_THRESHOLD
        ): List<Pair<RectF, Float>> {
            val sorted = candidates.sortedByDescending { it.second }
            val suppressed = BooleanArray(sorted.size)
            val kept = mutableListOf<Pair<RectF, Float>>()
            for (i in sorted.indices) {
                if (suppressed[i]) continue
                kept.add(sorted[i])
                for (j in i + 1 until sorted.size) {
                    if (!suppressed[j] && iou(sorted[i].first, sorted[j].first) > iouThreshold)
                        suppressed[j] = true
                }
            }
            return kept
        }

        fun iou(a: RectF, b: RectF): Float {
            val ix = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left))
            val iy = maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
            val inter = ix * iy
            if (inter == 0f) return 0f
            val aArea = (a.right - a.left) * (a.bottom - a.top)
            val bArea = (b.right - b.left) * (b.bottom - b.top)
            return inter / (aArea + bArea - inter)
        }
    }
}
