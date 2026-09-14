package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import android.graphics.RectF
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.data.BounceRepository
import com.example.tennisscorer.tracking.BounceEvent
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.Detection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallTrackingViewModelTest {

    private val mockEngine = mockk<TennisScoreEngine>(relaxed = true) {
        every { matchSavedEvent } returns MutableSharedFlow()
    }
    private val mockBounceRepo = mockk<BounceRepository>(relaxed = true)
    private val vm = BallTrackingViewModel(mockEngine, mockBounceRepo)

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

    @Test fun `heatmapBitmap starts null`() {
        assertNull(vm.heatmapBitmap.value)
    }

    @Test fun `bounceCount starts 0`() {
        assertEquals(0, vm.bounceCount.value)
    }

    @Test fun `handleBounceEvent PointAwarded calls engine pointWonBy and nulls trackedBall`() {
        val courtPos = android.graphics.PointF(5f, 3f)
        vm.handleBounceEvent(BounceEvent.PointAwarded(winner = 2, isOut = false, courtPos = courtPos))
        verify { mockEngine.pointWonBy(2) }
        assertNull(vm.trackedBall.value)
    }

    @Test fun `bounce without calibration does not call pointWonBy`() {
        repeat(20) { vm.processBallUpdate(null) }
        verify(exactly = 0) { mockEngine.pointWonBy(any()) }
    }

    @Test fun `real detection without calibration does not call pointWonBy`() {
        val detection = Detection(boundingBox = RectF(0.1f, 0.1f, 0.2f, 0.2f), confidence = 0.9f)
        vm.processBallUpdate(detection)
        vm.processBallUpdate(detection)
        verify(exactly = 0) { mockEngine.pointWonBy(any()) }
    }

    @Test fun `isRecording starts false`() {
        assertFalse(vm.isRecording.value)
    }

    @Test fun `recordingError starts null`() {
        assertNull(vm.recordingError.value)
    }

    @Test fun `isVideoAvailable starts false`() {
        assertFalse(vm.isVideoAvailable.value)
    }

    @Test fun `stopRecording when not recording does not crash`() {
        vm.stopRecording()
        assertFalse(vm.isRecording.value)
    }

    @Test fun `ballCourtPos starts null`() {
        assertNull(vm.ballCourtPos.value)
    }

    @Test fun `processBallUpdate without calibration keeps ballCourtPos null`() {
        val detection = Detection(boundingBox = RectF(0.5f, 0.5f, 0.6f, 0.6f), confidence = 0.9f)
        vm.processBallUpdate(detection)
        assertNull(vm.ballCourtPos.value)
    }

    @Test fun `processBallUpdate with null detection clears ballCourtPos`() {
        val detection = Detection(boundingBox = RectF(0.5f, 0.5f, 0.6f, 0.6f), confidence = 0.9f)
        vm.processBallUpdate(detection)
        vm.processBallUpdate(null)
        assertNull(vm.ballCourtPos.value)
    }

    @Test fun `startManualCalibration transitions to ManualCalibrating with empty taps`() {
        vm.startManualCalibration()
        val state = vm.calibrationState.value
        assertTrue(state is CalibrationState.ManualCalibrating)
        assertEquals(0, (state as CalibrationState.ManualCalibrating).taps.size)
    }

    @Test fun `addManualTap accumulates taps in ManualCalibrating state`() {
        vm.startManualCalibration()
        val tap1 = android.graphics.PointF().also { it.x = 0.1f; it.y = 0.9f }
        val tap2 = android.graphics.PointF().also { it.x = 0.9f; it.y = 0.9f }
        vm.addManualTap(tap1)
        vm.addManualTap(tap2)
        val state = vm.calibrationState.value as? CalibrationState.ManualCalibrating
        assertNotNull(state)
        assertEquals(2, state!!.taps.size)
        assertEquals(0.1f, state.taps[0].x, 0.001f)
        assertEquals(0.9f, state.taps[1].x, 0.001f)
    }

    @Test fun `addManualTap does nothing when not in ManualCalibrating state`() {
        vm.addManualTap(android.graphics.PointF(0.5f, 0.5f))
        assertTrue(vm.calibrationState.value is CalibrationState.Uncalibrated)
    }

    @Test fun `addManualTap with 3 taps stays in ManualCalibrating`() {
        vm.startManualCalibration()
        repeat(3) { i -> vm.addManualTap(android.graphics.PointF(i * 0.3f, i * 0.3f)) }
        val state = vm.calibrationState.value
        assertTrue(state is CalibrationState.ManualCalibrating)
        assertEquals(3, (state as CalibrationState.ManualCalibrating).taps.size)
    }
}
