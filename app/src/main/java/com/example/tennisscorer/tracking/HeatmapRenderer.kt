package com.example.tennisscorer.tracking

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.exp
import kotlin.math.roundToInt

object HeatmapRenderer {

    const val BITMAP_W = 220
    const val BITMAP_H = 476
    const val SIGMA_PX = 30f

    fun render(points: List<PointF>): Bitmap? {
        if (points.isEmpty()) return null

        val density = FloatArray(BITMAP_W * BITMAP_H)
        val radius = (3f * SIGMA_PX).toInt()
        val twoSigmaSq = 2f * SIGMA_PX * SIGMA_PX

        for (point in points) {
            val px = point.x / HomographyMapper.COURT_WIDTH_M * BITMAP_W
            val py = point.y / HomographyMapper.COURT_LENGTH_M * BITMAP_H
            for (dy in -radius..radius) {
                val iy = (py + dy).roundToInt()
                if (iy < 0 || iy >= BITMAP_H) continue
                for (dx in -radius..radius) {
                    val ix = (px + dx).roundToInt()
                    if (ix < 0 || ix >= BITMAP_W) continue
                    density[iy * BITMAP_W + ix] += exp(-(dx * dx + dy * dy) / twoSigmaSq)
                }
            }
        }

        val maxVal = density.max()
        if (maxVal == 0f) return null

        val pixels = IntArray(BITMAP_W * BITMAP_H) { i ->
            densityToArgb(density[i] / maxVal)
        }

        val bmp = Bitmap.createBitmap(BITMAP_W, BITMAP_H, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, BITMAP_W, 0, 0, BITMAP_W, BITMAP_H)
        return bmp
    }

    private fun densityToArgb(value: Float): Int {
        if (value < 0.05f) return 0x00000000
        return when {
            value < 0.25f -> lerpArgb(value / 0.25f,
                argb(100, 0, 0, 220), argb(160, 0, 220, 255))
            value < 0.5f  -> lerpArgb((value - 0.25f) / 0.25f,
                argb(160, 0, 220, 255), argb(200, 0, 220, 0))
            value < 0.75f -> lerpArgb((value - 0.5f) / 0.25f,
                argb(200, 0, 220, 0), argb(230, 255, 220, 0))
            else          -> lerpArgb((value - 0.75f) / 0.25f,
                argb(230, 255, 220, 0), argb(255, 255, 0, 0))
        }
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun lerpArgb(t: Float, c1: Int, c2: Int): Int {
        val a1 = (c1 ushr 24) and 0xFF; val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF;  val b1 = c1 and 0xFF
        val a2 = (c2 ushr 24) and 0xFF; val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF;  val b2 = c2 and 0xFF
        return argb(
            (a1 + t * (a2 - a1)).toInt(),
            (r1 + t * (r2 - r1)).toInt(),
            (g1 + t * (g2 - g1)).toInt(),
            (b1 + t * (b2 - b1)).toInt()
        )
    }
}
