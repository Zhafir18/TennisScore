package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.tracking.BallDetector
import com.example.tennisscorer.tracking.BounceDetector
import com.example.tennisscorer.tracking.BounceEvent
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.CourtDetector
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.FrameAnalyzer
import com.example.tennisscorer.tracking.HomographyMapper
import com.example.tennisscorer.tracking.HomographyResult
import com.example.tennisscorer.tracking.ImageAnalyzer
import com.example.tennisscorer.tracking.KalmanTracker
import com.example.tennisscorer.tracking.TrackedBall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BallTrackingViewModel(
    private val engine: TennisScoreEngine
) : ViewModel() {

    companion object {
        fun factory(engine: TennisScoreEngine): ViewModelProvider.Factory = viewModelFactory {
            initializer { BallTrackingViewModel(engine) }
        }
    }

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Uncalibrated)
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    private val _trackedBall = MutableStateFlow<TrackedBall?>(null)
    val trackedBall: StateFlow<TrackedBall?> = _trackedBall.asStateFlow()

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

    private val kalmanTracker = KalmanTracker()
    private val bounceDetector = BounceDetector()
    private var ballDetector: BallDetector? = null
    private var courtDetector: CourtDetector? = null

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

    internal fun processBallUpdate(detection: Detection?) {
        val trackedBall = kalmanTracker.update(detection)
        _trackedBall.value = trackedBall
        if (trackedBall != null) {
            val mapper = (_calibrationState.value as? CalibrationState.Calibrated)?.mapper
            val courtPos = mapper?.mapToCourtCoords(trackedBall.position)
            val event = bounceDetector.process(trackedBall.velocity.y, courtPos, trackedBall.isPredicted)
            if (event is BounceEvent.PointAwarded) {
                handleBounceEvent(event)
            }
        }
    }

    internal fun handleBounceEvent(event: BounceEvent) {
        if (event is BounceEvent.PointAwarded) {
            engine.pointWonBy(event.winner)
            kalmanTracker.reset()
            bounceDetector.reset()
            _trackedBall.value = null
        }
    }

    fun initDetector(context: Context) {
        if (ballDetector != null) return
        val detector = BallDetector(context.applicationContext) { detections ->
            _detections.value = detections
            processBallUpdate(detections.firstOrNull())
        }
        ballDetector = detector
        setFrameAnalyzer(detector)
    }

    fun resetTrajectory() {
        kalmanTracker.reset()
        bounceDetector.reset()
        _trackedBall.value = null
    }

    fun initCalibration(context: Context) {
        if (_calibrationState.value != CalibrationState.Uncalibrated) return
        val appContext = context.applicationContext
        _calibrationState.value = CalibrationState.Calibrating
        val detector = CourtDetector { result ->
            when (result) {
                is HomographyResult.Success ->
                    _calibrationState.value = CalibrationState.Calibrated(HomographyMapper(result.matrix))
                is HomographyResult.Failed ->
                    _calibrationState.value = CalibrationState.Failed(result.reason)
            }
            initDetector(appContext)
        }
        courtDetector = detector
        setFrameAnalyzer(detector)
    }

    override fun onCleared() {
        cameraExecutor.shutdown()
        courtDetector?.close()
        ballDetector?.close()
        super.onCleared()
    }
}
