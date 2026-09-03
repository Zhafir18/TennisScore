package com.example.tennisscorer.tracking

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanTrackerTest {

    private fun detection(l: Float, t: Float, r: Float, b: Float): Detection {
        val box = RectF()
        box.left = l; box.top = t; box.right = r; box.bottom = b
        return Detection(box, 0.9f)
    }

    @Test fun `update with null before init returns null`() {
        val tracker = KalmanTracker()
        assertNull(tracker.update(null))
    }

    @Test fun `first detection initializes position to bounding box center`() {
        val tracker = KalmanTracker()
        // cx = (0.2+0.4)/2 = 0.3, cy = (0.3+0.5)/2 = 0.4
        val result = tracker.update(detection(0.2f, 0.3f, 0.4f, 0.5f))!!
        assertEquals(0.3f, result.position.x, 0.001f)
        assertEquals(0.4f, result.position.y, 0.001f)
        assertFalse(result.isPredicted)
        assertEquals(0f, result.velocity.x, 0.001f)
        assertEquals(0f, result.velocity.y, 0.001f)
    }

    @Test fun `second detection smooths position toward observation`() {
        val tracker = KalmanTracker()
        tracker.update(detection(0.2f, 0.3f, 0.4f, 0.5f))   // cx=0.3, cy=0.4
        // second detection at cx=0.5, cy=0.6 — should blend with prediction (0.3,0.4)
        val result = tracker.update(detection(0.4f, 0.5f, 0.6f, 0.7f))!!
        assertTrue("x should move from prediction 0.3 toward observation 0.5",
            result.position.x > 0.3f && result.position.x < 0.5f)
        assertTrue("y should move from prediction 0.4 toward observation 0.6",
            result.position.y > 0.4f && result.position.y < 0.6f)
        assertFalse(result.isPredicted)
        assertTrue("vx should be positive (moving right)", result.velocity.x > 0f)
        assertTrue("vy should be positive (moving down)", result.velocity.y > 0f)
    }

    @Test fun `update with null after init returns isPredicted true`() {
        val tracker = KalmanTracker()
        tracker.update(detection(0.2f, 0.3f, 0.4f, 0.5f))
        val result = tracker.update(null)
        assertNotNull(result)
        assertTrue(result!!.isPredicted)
    }

    @Test fun `trajectory accumulates across frames`() {
        val tracker = KalmanTracker()
        val d = detection(0.1f, 0.1f, 0.3f, 0.3f)
        tracker.update(d)
        tracker.update(d)
        tracker.update(null)
        val result = tracker.update(d)!!
        assertEquals(4, result.trajectory.size)
    }

    @Test fun `reset clears trajectory and returns null until next detection`() {
        val tracker = KalmanTracker()
        val d = detection(0.1f, 0.1f, 0.3f, 0.3f)
        tracker.update(d)
        tracker.update(d)
        tracker.reset()
        assertNull(tracker.update(null))
        val result = tracker.update(d)!!
        assertEquals(1, result.trajectory.size)
    }
}
