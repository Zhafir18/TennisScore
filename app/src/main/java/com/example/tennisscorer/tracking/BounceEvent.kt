package com.example.tennisscorer.tracking

import android.graphics.PointF

sealed class BounceEvent {
    data class PointAwarded(
        val winner: Int,
        val isOut: Boolean,
        val courtPos: PointF
    ) : BounceEvent()
}
