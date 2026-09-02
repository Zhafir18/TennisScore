package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.tennisscorer.tracking.BallDetector
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.FrameAnalyzer
import com.example.tennisscorer.tracking.ImageAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BallTrackingViewModel : ViewModel() {

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

    private var ballDetector: BallDetector? = null

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun onCameraError(message: String) {
        _cameraError.value = message
    }

    fun setFrameAnalyzer(analyzer: FrameAnalyzer) {
        imageAnalyzer.setFrameAnalyzer(analyzer)
    }

    fun clearFrameAnalyzer() {
        imageAnalyzer.setFrameAnalyzer(null)
    }

    fun initDetector(context: Context) {
        if (ballDetector != null) return
        val detector = BallDetector(context.applicationContext) { _detections.value = it }
        ballDetector = detector
        setFrameAnalyzer(detector)
    }

    override fun onCleared() {
        ballDetector?.close()
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
