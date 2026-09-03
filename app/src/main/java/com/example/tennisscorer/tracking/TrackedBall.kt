package com.example.tennisscorer.tracking

import android.graphics.PointF

// NOTE: PointF does not override equals/hashCode — TrackedBall structural equality
// compares object identity for position, velocity, and trajectory elements.
// Do not use == or distinctUntilChanged on TrackedBall.
data class TrackedBall(
    val position: PointF,
    val velocity: PointF,
    val isPredicted: Boolean,
    val trajectory: List<PointF>
)
