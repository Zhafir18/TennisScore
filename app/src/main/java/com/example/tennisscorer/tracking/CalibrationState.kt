package com.example.tennisscorer.tracking

import android.graphics.PointF

sealed class CalibrationState {
    object Uncalibrated : CalibrationState()
    object Calibrating : CalibrationState()
    data class ManualCalibrating(val taps: List<PointF> = emptyList()) : CalibrationState()
    data class Calibrated(val mapper: HomographyMapper) : CalibrationState()
    data class Failed(val reason: String) : CalibrationState()
}
