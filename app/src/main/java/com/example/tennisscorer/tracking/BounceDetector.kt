package com.example.tennisscorer.tracking

import android.graphics.PointF

class BounceDetector {

    companion object {
        const val BOUNCE_COOLDOWN_FRAMES = 10
        const val VELOCITY_THRESHOLD = 0.005f
        val NET_Y_M = HomographyMapper.COURT_LENGTH_M / 2f  // 11.885f
    }

    private var previousVy     = 0f
    private var cooldownFrames = 0
    private var lastSide       = 0   // 0=unknown, 1=P1 half (y<NET), 2=P2 half (y≥NET)
    private var bounceCountP1  = 0
    private var bounceCountP2  = 0

    fun process(vy: Float, courtPos: PointF?, isPredicted: Boolean): BounceEvent? {
        // Step 1: net crossing detection — runs every frame regardless of cooldown
        if (courtPos != null) {
            val currentSide = if (courtPos.y < NET_Y_M) 1 else 2
            if (lastSide != 0 && currentSide != lastSide) {
                if (currentSide == 1) bounceCountP1 = 0 else bounceCountP2 = 0
            }
            lastSide = currentSide
        }

        // Step 2a: predicted guard (no sensor input — skip, do not count down cooldown)
        if (isPredicted) {
            previousVy = vy
            return null
        }
        // Step 2b: cooldown guard
        if (cooldownFrames > 0) {
            cooldownFrames--
            previousVy = vy
            return null
        }

        // Step 3: bounce detection — vy sign change from positive to negative
        val bounce = previousVy > VELOCITY_THRESHOLD && vy < -VELOCITY_THRESHOLD
        previousVy = vy
        if (!bounce || courtPos == null) return null

        cooldownFrames = BOUNCE_COOLDOWN_FRAMES

        val isIn = courtPos.x >= 0f && courtPos.x <= HomographyMapper.COURT_WIDTH_M &&
                   courtPos.y >= 0f && courtPos.y <= HomographyMapper.COURT_LENGTH_M

        // Step 4: scoring
        return if (isIn) {
            if (courtPos.y < NET_Y_M) {
                bounceCountP1++
                if (bounceCountP1 >= 2) BounceEvent.PointAwarded(winner = 2, isOut = false, courtPos = courtPos)
                else null
            } else {
                bounceCountP2++
                if (bounceCountP2 >= 2) BounceEvent.PointAwarded(winner = 1, isOut = false, courtPos = courtPos)
                else null
            }
        } else {
            val winner = when {
                courtPos.y < 0f                                  -> 1
                courtPos.y > HomographyMapper.COURT_LENGTH_M     -> 2
                else                                             -> if (lastSide == 1) 2 else 1  // lastSide==0 edge case: defaults to P1 wins (first frame out is very rare)
            }
            BounceEvent.PointAwarded(winner = winner, isOut = true, courtPos = courtPos)
        }
    }

    fun reset() {
        previousVy     = 0f
        cooldownFrames = 0
        lastSide       = 0
        bounceCountP1  = 0
        bounceCountP2  = 0
    }
}
