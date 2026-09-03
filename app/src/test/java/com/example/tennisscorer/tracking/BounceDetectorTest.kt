package com.example.tennisscorer.tracking

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BounceDetectorTest {

    private lateinit var detector: BounceDetector

    private val W   = HomographyMapper.COURT_WIDTH_M    // 10.97f
    private val L   = HomographyMapper.COURT_LENGTH_M   // 23.77f
    private val NET = BounceDetector.NET_Y_M            // 11.885f
    private val VY  = BounceDetector.VELOCITY_THRESHOLD + 0.01f  // just above threshold

    private fun pos(x: Float, y: Float) = PointF().also { it.x = x; it.y = y }

    @Before fun setUp() { detector = BounceDetector() }

    @Test fun `process before vy sign change returns null`() {
        val p = pos(W / 2f, NET / 2f)
        assertNull(detector.process(VY, p, false))
        assertNull(detector.process(VY, p, false))
    }

    @Test fun `isPredicted frame skipped — no bounce triggered`() {
        val p = pos(W / 2f, NET / 2f)
        detector.process(VY, p, false)         // prime previousVy = VY
        // isPredicted=true: should NOT detect bounce even with sign change
        assertNull(detector.process(-VY, p, true))
    }

    @Test fun `first bounce IN P1 half returns null — rally continues`() {
        val p = pos(W / 2f, NET / 2f - 1f)   // y < NET → P1 half
        detector.process(VY, p, false)
        assertNull(detector.process(-VY, p, false))
    }

    @Test fun `second bounce IN P1 half returns PointAwarded winner=2`() {
        val p = pos(W / 2f, NET / 2f - 1f)   // P1 half
        // First bounce → null
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        // Drain cooldown (10 frames, each with VY so previousVy=VY when cooldown ends)
        repeat(BounceDetector.BOUNCE_COOLDOWN_FRAMES) { detector.process(VY, p, false) }
        // Second bounce
        detector.process(VY, p, false)        // re-prime after drain
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(2, result.winner)
        assertFalse(result.isOut)
    }

    @Test fun `first bounce IN P2 half returns null — rally continues`() {
        val p = pos(W / 2f, NET + 1f)        // y > NET → P2 half
        detector.process(VY, p, false)
        assertNull(detector.process(-VY, p, false))
    }

    @Test fun `second bounce IN P2 half returns PointAwarded winner=1`() {
        val p = pos(W / 2f, NET + 1f)        // P2 half
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        repeat(BounceDetector.BOUNCE_COOLDOWN_FRAMES) { detector.process(VY, p, false) }
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(1, result.winner)
        assertFalse(result.isOut)
    }

    @Test fun `net crossing resets bounce count — first bounce after crossing returns null`() {
        val posP1 = pos(W / 2f, NET / 2f - 1f)  // P1 half
        val posP2 = pos(W / 2f, NET + 1f)        // P2 half
        // First bounce on P1 side
        detector.process(VY, posP1, false)
        detector.process(-VY, posP1, false)
        repeat(BounceDetector.BOUNCE_COOLDOWN_FRAMES) { detector.process(VY, posP1, false) }
        // Ball crosses to P2 side (resets bounceCountP2) then back to P1 (resets bounceCountP1)
        detector.process(VY, posP2, false)
        detector.process(VY, posP1, false)
        // Re-prime and bounce on P1 — count was reset, so this is first → null
        detector.process(VY, posP1, false)
        val result = detector.process(-VY, posP1, false)
        assertNull(result)
    }

    @Test fun `bounce OUT courtPos y below 0 returns winner=1`() {
        // Ball past P1 baseline (y < 0) → P2 overshot → P1 wins
        val p = pos(W / 2f, -1f)
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(1, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `bounce OUT courtPos y above COURT_LENGTH returns winner=2`() {
        // Ball past P2 baseline (y > L) → P1 overshot → P2 wins
        val p = pos(W / 2f, L + 1f)
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(2, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `bounce OUT sideline lastSide=2 returns PointAwarded winner=1`() {
        // Set lastSide=2 via a P2-half tracking frame (vy=0f — below threshold, no bounce)
        detector.process(0f, pos(W / 2f, NET + 1f), false)
        // Bounce OUT at sideline (x < 0) in P2 half
        val outPos = pos(-1f, NET + 1f)
        detector.process(VY, outPos, false)
        val result = detector.process(-VY, outPos, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(1, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `bounce OUT sideline with lastSide=1 returns winner=2`() {
        // Set lastSide=1 via P1-half tracking frame
        detector.process(0f, pos(W / 2f, NET / 2f - 1f), false)
        // Bounce OUT at sideline (x < 0) still in P1 half y
        val outPos = pos(-1f, NET / 2f - 1f)
        detector.process(VY, outPos, false)
        val result = detector.process(-VY, outPos, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(2, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `cooldown prevents double detection`() {
        val p = pos(W / 2f, NET + 1f)   // P2 half
        // First bounce (bounceCountP2 = 1)
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        // Immediately try second bounce (cooldown = 10, not drained)
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNull(result)  // still in cooldown → null
    }

    @Test fun `reset clears all state — first bounce after reset returns null`() {
        val p = pos(W / 2f, NET + 1f)   // P2 half
        // First bounce (bounceCountP2 = 1, cooldown = 10)
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        // Reset clears bounce count and cooldown
        detector.reset()
        // After reset: fresh start — next two calls should behave as first-ever bounce → null
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNull(result)  // bounceCountP2 was reset to 0, now = 1 < 2 → null
    }
}
