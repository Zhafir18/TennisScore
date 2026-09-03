package com.example.tennisscorer.tracking

import android.graphics.PointF

data class TrackedBall(
    val position: PointF,
    val velocity: PointF,
    val isPredicted: Boolean,
    val trajectory: List<PointF>
)
