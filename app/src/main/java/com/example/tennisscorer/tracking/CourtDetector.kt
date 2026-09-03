package com.example.tennisscorer.tracking

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2

class CourtDetector(
    private val onResult: (HomographyResult) -> Unit
) : FrameAnalyzer {

    companion object {
        const val MAX_FRAMES = 60
        private const val CANNY_LOW = 50.0
        private const val CANNY_HIGH = 150.0
        private const val HOUGH_THRESHOLD = 80
        private const val HOUGH_MIN_LINE_LENGTH = 100.0
        private const val HOUGH_MAX_LINE_GAP = 10.0
        private const val ANGLE_TOLERANCE_DEG = 20.0
        private const val MIN_QUAD_AREA_RATIO = 0.15
    }

    private var framesProcessed = 0
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) { image.close(); return }
        val bitmap = image.toBitmap()
        image.close()
        framesProcessed++
        val matrix = tryDetectCourt(bitmap)
        when {
            matrix != null -> {
                done = true
                onResult(HomographyResult.Success(matrix))
            }
            framesProcessed >= MAX_FRAMES -> {
                done = true
                onResult(HomographyResult.Failed("Lapangan tidak terdeteksi setelah $MAX_FRAMES frame"))
            }
        }
    }

    fun close() { /* Mat released per-frame in tryDetectCourt — no persistent state */ }

    private fun tryDetectCourt(bitmap: Bitmap): FloatArray? {
        val rgbaMat = Mat(); val grayMat = Mat(); val edgesMat = Mat(); val linesMat = Mat()
        var H: Mat? = null; var src: MatOfPoint2f? = null; var dst: MatOfPoint2f? = null
        return try {
            Utils.bitmapToMat(bitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Canny(grayMat, edgesMat, CANNY_LOW, CANNY_HIGH)
            Imgproc.HoughLinesP(
                edgesMat, linesMat, 1.0, Math.PI / 180.0,
                HOUGH_THRESHOLD, HOUGH_MIN_LINE_LENGTH, HOUGH_MAX_LINE_GAP
            )

            val horizontals = mutableListOf<DoubleArray>()
            val verticals   = mutableListOf<DoubleArray>()
            for (i in 0 until linesMat.rows()) {
                val seg = linesMat.get(i, 0)
                val dx = seg[2] - seg[0]; val dy = seg[3] - seg[1]
                val angleDeg = Math.toDegrees(atan2(abs(dy), abs(dx)))
                when {
                    angleDeg < ANGLE_TOLERANCE_DEG -> horizontals.add(seg)
                    angleDeg > (90.0 - ANGLE_TOLERANCE_DEG) -> verticals.add(seg)
                }
            }
            if (horizontals.size < 2 || verticals.size < 2) return null

            // Near baseline = larger avg-Y (lower in image); Far = smaller avg-Y
            // Left sideline = smaller avg-X; Right sideline = larger avg-X
            val sortedH = horizontals.sortedByDescending { (it[1] + it[3]) / 2.0 }
            val sortedV = verticals.sortedBy { (it[0] + it[2]) / 2.0 }
            val hNear = sortedH[0]; val hFar = sortedH[1]
            val vLeft = sortedV[0]; val vRight = sortedV[1]

            val bl = intersect(vLeft, hNear)  ?: return null  // near-left  → (0, 0)
            val br = intersect(vRight, hNear) ?: return null  // near-right → (WIDTH, 0)
            val tl = intersect(vLeft, hFar)   ?: return null  // far-left   → (0, LENGTH)
            val tr = intersect(vRight, hFar)  ?: return null  // far-right  → (WIDTH, LENGTH)

            if (shoelaceArea(listOf(bl, br, tr, tl)) <
                MIN_QUAD_AREA_RATIO * bitmap.width * bitmap.height) return null

            src = MatOfPoint2f(
                Point(bl.x.toDouble(), bl.y.toDouble()),
                Point(br.x.toDouble(), br.y.toDouble()),
                Point(tl.x.toDouble(), tl.y.toDouble()),
                Point(tr.x.toDouble(), tr.y.toDouble())
            )
            dst = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(HomographyMapper.COURT_WIDTH_M.toDouble(), 0.0),
                Point(0.0, HomographyMapper.COURT_LENGTH_M.toDouble()),
                Point(HomographyMapper.COURT_WIDTH_M.toDouble(), HomographyMapper.COURT_LENGTH_M.toDouble())
            )
            H = Imgproc.getPerspectiveTransform(src, dst)
            FloatArray(9) { i -> H.get(i / 3, i % 3)[0].toFloat() }
        } catch (_: Exception) {
            null
        } finally {
            rgbaMat.release(); grayMat.release(); edgesMat.release(); linesMat.release()
            H?.release(); src?.release(); dst?.release()
        }
    }

    private fun intersect(s1: DoubleArray, s2: DoubleArray): PointF? {
        val x1 = s1[0]; val y1 = s1[1]; val x2 = s1[2]; val y2 = s1[3]
        val x3 = s2[0]; val y3 = s2[1]; val x4 = s2[2]; val y4 = s2[3]
        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-10) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
        return PointF((x1 + t * (x2 - x1)).toFloat(), (y1 + t * (y2 - y1)).toFloat())
    }

    private fun shoelaceArea(pts: List<PointF>): Double {
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }
}
