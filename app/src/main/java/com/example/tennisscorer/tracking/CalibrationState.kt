package com.example.tennisscorer.tracking

sealed class CalibrationState {
    object Uncalibrated : CalibrationState()
    object Calibrating : CalibrationState()
    data class Calibrated(val mapper: HomographyMapper) : CalibrationState()
    data class Failed(val reason: String) : CalibrationState()
}
