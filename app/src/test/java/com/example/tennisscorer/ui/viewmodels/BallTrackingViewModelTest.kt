package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.tracking.BounceEvent
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.TrackedBall
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallTrackingViewModelTest {

    private val mockEngine = mockk<TennisScoreEngine>(relaxed = true)
    private val vm = BallTrackingViewModel(mockEngine)

    @After fun tearDown() { vm.cameraExecutor.shutdown() }

    @Test fun `permissionGranted starts false`() {
        assertFalse(vm.permissionGranted.value)
    }

    @Test fun `onPermissionResult true sets permissionGranted true`() {
        vm.onPermissionResult(true)
        assertTrue(vm.permissionGranted.value)
    }

    @Test fun `onPermissionResult false keeps permissionGranted false`() {
        vm.onPermissionResult(true)
        vm.onPermissionResult(false)
        assertFalse(vm.permissionGranted.value)
    }

    @Test fun `cameraError starts null`() {
        assertNull(vm.cameraError.value)
    }

    @Test fun `onCameraError sets cameraError message`() {
        vm.onCameraError("Kamera tidak dapat dibuka")
        assertEquals("Kamera tidak dapat dibuka", vm.cameraError.value)
    }

    @Test fun `cameraExecutor not null`() {
        assertNotNull(vm.cameraExecutor)
    }

    @Test fun `detections starts empty`() {
        assertTrue(vm.detections.value.isEmpty())
    }

    @Test fun `calibrationState starts Uncalibrated`() {
        assertTrue(vm.calibrationState.value is CalibrationState.Uncalibrated)
    }

    @Test fun `initCalibration transitions to Calibrating`() {
        val ctx = mockk<Context>(relaxed = true)
        vm.initCalibration(ctx)
        assertTrue(vm.calibrationState.value is CalibrationState.Calibrating)
    }

    @Test fun `initCalibration is idempotent`() {
        val ctx = mockk<Context>(relaxed = true)
        vm.initCalibration(ctx)
        vm.initCalibration(ctx)
        assertTrue(vm.calibrationState.value is CalibrationState.Calibrating)
    }

    @Test fun `trackedBall starts null`() {
        assertNull(vm.trackedBall.value)
    }

    @Test fun `resetTrajectory sets trackedBall to null`() {
        vm.resetTrajectory()
        assertNull(vm.trackedBall.value)
    }

    // --- Task 2 new tests ---

    @Test fun `handleBounceEvent PointAwarded calls engine pointWonBy and nulls trackedBall`() {
        val courtPos = PointF().also { it.x = 5f; it.y = 3f }
        vm.handleBounceEvent(BounceEvent.PointAwarded(winner = 2, isOut = false, courtPos = courtPos))
        verify { mockEngine.pointWonBy(2) }
        assertNull(vm.trackedBall.value)
    }

    @Test fun `bounce without calibration does not call pointWonBy`() {
        // calibrationState = Uncalibrated (default) → mapper=null → courtPos=null
        // → bounceDetector.process(vy, null, ...) returns null → engine never called
        // Send 20 identical detections — vy stays near 0, courtPos=null regardless
        repeat(20) { vm.processBallUpdate(null) }
        verify(exactly = 0) { mockEngine.pointWonBy(any()) }
    }

    @Test fun `real detection without calibration does not call pointWonBy`() {
        // Supplies real Detection objects (not null) so KalmanTracker produces isPredicted=false,
        // but calibrationState=Uncalibrated (default) → mapper=null → courtPos=null.
        // This exercises the courtPos=null code path in processBallUpdate on real detections.
        val detection = Detection(
            boundingBox = RectF(0.1f, 0.1f, 0.2f, 0.2f),
            confidence = 0.9f
        )
        vm.processBallUpdate(detection)
        vm.processBallUpdate(detection)
        verify(exactly = 0) { mockEngine.pointWonBy(any()) }
    }
}
