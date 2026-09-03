package com.example.tennisscorer.tracking

import android.graphics.PointF

class KalmanTracker {

    companion object {
        const val PROCESS_NOISE_Q = 1e-4f
        const val MEASUREMENT_NOISE_R = 1e-2f

        private fun pointF(x: Float, y: Float): PointF {
            val p = PointF()
            p.x = x
            p.y = y
            return p
        }

        // F: 4x4 state transition matrix, row-major
        // [1,0,1,0]  cx  += vx
        // [0,1,0,1]  cy  += vy
        // [0,0,1,0]  vx  unchanged
        // [0,0,0,1]  vy  unchanged
        private val F = floatArrayOf(
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        private val FT = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
        )
        // H: 2x4 observation matrix — observe cx and cy only
        private val H = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f
        )
        // HT: 4x2 transpose of H
        private val HT = floatArrayOf(
            1f, 0f,
            0f, 1f,
            0f, 0f,
            0f, 0f
        )
    }

    private var x = FloatArray(4)   // state [cx, cy, vx, vy]
    private var P = FloatArray(16)  // covariance 4x4, row-major
    private var initialized = false
    private val _trajectory = mutableListOf<PointF>()

    fun update(detection: Detection?): TrackedBall? {
        if (!initialized) {
            if (detection == null) return null
            val cx = (detection.boundingBox.left + detection.boundingBox.right) / 2f
            val cy = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f
            x = floatArrayOf(cx, cy, 0f, 0f)
            P = floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
            initialized = true
            _trajectory.add(pointF(cx, cy))
            return TrackedBall(pointF(x[0], x[1]), pointF(x[2], x[3]), false, _trajectory.toList())
        }

        // Predict: x̂ = F·x,  P̂ = F·P·Fᵀ + Q·I
        val xPred = m4v4(F, x)
        val pPred = m4m4addQ(m4m4(m4m4(F, P), FT))

        val isPredicted = detection == null
        if (!isPredicted) {
            val cx = (detection!!.boundingBox.left + detection.boundingBox.right) / 2f
            val cy = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f

            // innovation: y = z - H·x̂  (H selects first 2 elements)
            val y = floatArrayOf(cx - xPred[0], cy - xPred[1])

            // S = H·P̂·Hᵀ + R·I₂  (H picks rows 0,1 of P̂; Hᵀ picks cols 0,1 → S = P̂[0..1, 0..1] + R·I₂)
            val s00 = pPred[0] + MEASUREMENT_NOISE_R
            val s01 = pPred[1]
            val s10 = pPred[4]
            val s11 = pPred[5] + MEASUREMENT_NOISE_R
            val det = s00 * s11 - s01 * s10
            val sInv = floatArrayOf(s11 / det, -s01 / det, -s10 / det, s00 / det)

            // K = P̂·Hᵀ·S⁻¹  (P̂·Hᵀ picks cols 0,1 of P̂)
            val pHt = m4x4m4x2(pPred, HT)
            val K = m4x2m2x2(pHt, sInv)

            // x = x̂ + K·y
            x = FloatArray(4) { i -> xPred[i] + K[i * 2] * y[0] + K[i * 2 + 1] * y[1] }

            // P = (I - K·H)·P̂
            val KH = m4x2m2x4(K, H)
            val IKH = FloatArray(16) { i -> (if (i % 5 == 0) 1f else 0f) - KH[i] }
            P = m4m4(IKH, pPred)
        } else {
            x = xPred
            P = pPred
        }

        _trajectory.add(pointF(x[0], x[1]))
        return TrackedBall(pointF(x[0], x[1]), pointF(x[2], x[3]), isPredicted, _trajectory.toList())
    }

    fun reset() {
        initialized = false
        x = FloatArray(4)
        P = FloatArray(16)
        _trajectory.clear()
    }

    // 4x4 · 4 → 4
    private fun m4v4(m: FloatArray, v: FloatArray) = FloatArray(4) { i ->
        m[i*4]*v[0] + m[i*4+1]*v[1] + m[i*4+2]*v[2] + m[i*4+3]*v[3]
    }

    // 4x4 · 4x4 → 4x4
    private fun m4m4(a: FloatArray, b: FloatArray) = FloatArray(16) { k ->
        val i = k / 4; val j = k % 4
        a[i*4]*b[j] + a[i*4+1]*b[4+j] + a[i*4+2]*b[8+j] + a[i*4+3]*b[12+j]
    }

    // 4x4 + Q·I → 4x4
    private fun m4m4addQ(a: FloatArray) = FloatArray(16) { i ->
        a[i] + if (i % 5 == 0) PROCESS_NOISE_Q else 0f
    }

    // 4x4 · 4x2 → 4x2
    private fun m4x4m4x2(a: FloatArray, b: FloatArray) = FloatArray(8) { k ->
        val i = k / 2; val j = k % 2
        a[i*4]*b[j] + a[i*4+1]*b[2+j] + a[i*4+2]*b[4+j] + a[i*4+3]*b[6+j]
    }

    // 4x2 · 2x2 → 4x2
    private fun m4x2m2x2(a: FloatArray, b: FloatArray) = FloatArray(8) { k ->
        val i = k / 2; val j = k % 2
        a[i*2]*b[j] + a[i*2+1]*b[2+j]
    }

    // 4x2 · 2x4 → 4x4
    private fun m4x2m2x4(a: FloatArray, b: FloatArray) = FloatArray(16) { k ->
        val i = k / 4; val j = k % 4
        a[i*2]*b[j] + a[i*2+1]*b[4+j]
    }
}
