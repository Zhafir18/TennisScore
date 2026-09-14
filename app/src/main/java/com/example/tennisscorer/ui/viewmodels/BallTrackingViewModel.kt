package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.data.BounceRecord
import com.example.tennisscorer.data.BounceRepository
import com.example.tennisscorer.tracking.YoloV8Detector
import com.example.tennisscorer.tracking.BounceDetector
import com.example.tennisscorer.tracking.BounceEvent
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.CourtDetector
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.HeatmapRenderer
import com.example.tennisscorer.tracking.HomographyMapper
import com.example.tennisscorer.tracking.HomographyResult
import com.example.tennisscorer.tracking.ImageAnalyzer
import com.example.tennisscorer.tracking.KalmanTracker
import com.example.tennisscorer.tracking.TrackedBall
import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BallTrackingViewModel(
    private val engine: TennisScoreEngine,
    private val bounceRepo: BounceRepository
) : ViewModel() {

    companion object {
        fun factory(engine: TennisScoreEngine, bounceRepo: BounceRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { BallTrackingViewModel(engine, bounceRepo) }
        }
    }

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _ballDetectorError = MutableStateFlow<String?>(null)
    val ballDetectorError: StateFlow<String?> = _ballDetectorError.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Uncalibrated)
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    private val _trackedBall = MutableStateFlow<TrackedBall?>(null)
    val trackedBall: StateFlow<TrackedBall?> = _trackedBall.asStateFlow()

    private val _heatmapBitmap = MutableStateFlow<Bitmap?>(null)
    val heatmapBitmap: StateFlow<Bitmap?> = _heatmapBitmap.asStateFlow()

    private val _bounceCount = MutableStateFlow(0)
    val bounceCount: StateFlow<Int> = _bounceCount.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

    private val _isVideoAvailable = MutableStateFlow(false)
    val isVideoAvailable: StateFlow<Boolean> = _isVideoAvailable.asStateFlow()

    private var _videoCapture: VideoCapture<Recorder>? = null
    val videoCapture: VideoCapture<Recorder>? get() = _videoCapture

    private var activeRecording: Recording? = null

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

    private val kalmanTracker = KalmanTracker()
    private val bounceDetector = BounceDetector()
    private var ballDetector: YoloV8Detector? = null
    private var courtDetector: CourtDetector? = null

    private val bouncePoints = mutableListOf<PointF>()
    private val pendingBounces = mutableListOf<BounceRecord>()

    // Use a dedicated scope to avoid requiring Dispatchers.Main in unit tests
    private val backgroundScope = CoroutineScope(SupervisorJob())

    init {
        backgroundScope.launch {
            engine.matchSavedEvent.collect { matchId ->
                val toSave = pendingBounces.map { it.copy(matchId = matchId) }
                pendingBounces.clear()
                launch(Dispatchers.IO) { toSave.forEach { bounceRepo.insert(it) } }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun onCameraError(message: String) {
        _cameraError.value = message
    }

    fun setFrameAnalyzer(analyzer: com.example.tennisscorer.tracking.FrameAnalyzer?) {
        imageAnalyzer.setFrameAnalyzer(analyzer)
    }

    fun clearFrameAnalyzer() {
        imageAnalyzer.setFrameAnalyzer(null)
    }

    internal fun processBallUpdate(detection: Detection?) {
        val tracked = kalmanTracker.update(detection)
        _trackedBall.value = tracked
        if (tracked != null) {
            val mapper = (_calibrationState.value as? CalibrationState.Calibrated)?.mapper
            val courtPos = mapper?.mapToCourtCoords(tracked.position)
            val event = bounceDetector.process(tracked.velocity.y, courtPos, tracked.isPredicted)
            if (event is BounceEvent.PointAwarded) handleBounceEvent(event)
        }
    }

    internal fun handleBounceEvent(event: BounceEvent) {
        if (event is BounceEvent.PointAwarded) {
            engine.pointWonBy(event.winner)

            bouncePoints.add(event.courtPos)
            pendingBounces.add(
                BounceRecord(matchId = 0, x = event.courtPos.x, y = event.courtPos.y, player = event.winner)
            )
            val snapshot = bouncePoints.toList()
            backgroundScope.launch(Dispatchers.Default) {
                _heatmapBitmap.value = HeatmapRenderer.render(snapshot)
                _bounceCount.value = snapshot.size
            }

            kalmanTracker.reset()
            bounceDetector.reset()
            _trackedBall.value = null
        }
    }

    fun initDetector(context: Context) {
        if (ballDetector != null) return
        try {
            val detector = YoloV8Detector(context.applicationContext) { detections ->
                _detections.value = detections
                processBallUpdate(detections.firstOrNull())
            }
            ballDetector = detector
            setFrameAnalyzer(detector)
        } catch (e: Throwable) {
            _ballDetectorError.value = "Ball detection tidak tersedia di perangkat ini"
        }
    }

    fun resetTrajectory() {
        kalmanTracker.reset()
        bounceDetector.reset()
        _trackedBall.value = null
    }

    fun initVideoCapture() {
        if (_videoCapture != null) return
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        _videoCapture = VideoCapture.withOutput(recorder)
        _isVideoAvailable.value = true
    }

    fun setVideoUnavailable() {
        _videoCapture = null
        _isVideoAvailable.value = false
    }

    fun startRecording(context: Context, audioGranted: Boolean) {
        val recorder = _videoCapture?.output ?: return
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Tennis_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TennisScorer")
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val pending = recorder.prepareRecording(context, outputOptions)
        if (audioGranted) pending.withAudioEnabled()
        activeRecording = pending.start(cameraExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> _isRecording.value = true
                is VideoRecordEvent.Finalize -> {
                    _isRecording.value = false
                    if (event.hasError()) _recordingError.value = "Rekaman gagal disimpan"
                }
                else -> {}
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false
    }

    fun clearRecordingError() {
        _recordingError.value = null
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
        activeRecording?.stop()
        backgroundScope.cancel()
        cameraExecutor.shutdown()
        courtDetector?.close()
        ballDetector?.close()
        super.onCleared()
    }
}
