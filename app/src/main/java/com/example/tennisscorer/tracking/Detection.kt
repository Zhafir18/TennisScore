package com.example.tennisscorer.tracking

import android.graphics.RectF

data class Detection(
    val boundingBox: RectF,
    val confidence: Float
)
