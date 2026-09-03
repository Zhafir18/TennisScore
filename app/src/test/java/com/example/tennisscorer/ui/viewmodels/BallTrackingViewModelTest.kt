package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import com.example.tennisscorer.tracking.CalibrationState
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallTrackingViewModelTest {

    private val vm = BallTrackingViewModel()

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
}
