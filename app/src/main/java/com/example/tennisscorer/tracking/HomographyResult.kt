package com.example.tennisscorer.tracking

sealed class HomographyResult {
    class Success(val matrix: FloatArray) : HomographyResult() {
        override fun equals(other: Any?): Boolean =
            other is Success && matrix.contentEquals(other.matrix)
        override fun hashCode(): Int = matrix.contentHashCode()
    }
    data class Failed(val reason: String) : HomographyResult()
}
