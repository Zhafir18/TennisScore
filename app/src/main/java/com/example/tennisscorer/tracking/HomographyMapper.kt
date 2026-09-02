package com.example.tennisscorer.tracking

import android.graphics.PointF

class HomographyMapper(private val matrix: FloatArray) {
    // matrix: FloatArray(9), 3×3 row-major homography (from OpenCV getPerspectiveTransform)

    fun mapToCourtCoords(normalizedPoint: PointF): PointF {
        val (x, y) = mapNormalized(normalizedPoint.x, normalizedPoint.y)
        return PointF(x, y)
    }

    internal fun mapNormalized(nx: Float, ny: Float): Pair<Float, Float> {
        val w = matrix[6] * nx + matrix[7] * ny + matrix[8]
        return Pair(
            (matrix[0] * nx + matrix[1] * ny + matrix[2]) / w,
            (matrix[3] * nx + matrix[4] * ny + matrix[5]) / w
        )
    }

    companion object {
        const val COURT_WIDTH_M = 10.97f    // doubles sideline to sideline
        const val COURT_LENGTH_M = 23.77f   // baseline to baseline
    }
}
