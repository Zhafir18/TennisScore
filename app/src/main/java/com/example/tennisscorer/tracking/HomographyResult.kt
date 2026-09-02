package com.example.tennisscorer.tracking

sealed class HomographyResult {
    data class Success(val matrix: FloatArray) : HomographyResult()
    data class Failed(val reason: String) : HomographyResult()
}
