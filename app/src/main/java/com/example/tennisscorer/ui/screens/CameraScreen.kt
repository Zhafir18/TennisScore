package com.example.tennisscorer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.TrackedBall
import com.example.tennisscorer.ui.theme.ActionBtnBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.viewmodels.BallTrackingViewModel

@Composable
fun CameraScreen(
    engine: TennisScoreEngine,
    viewModel: BallTrackingViewModel = viewModel(factory = BallTrackingViewModel.factory(engine)),
    onBack: () -> Unit
) {
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val cameraError by viewModel.cameraError.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(lifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            cameraError != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = cameraError ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
                    ) { Text("Kembali", color = Color.White) }
                }
            }

            !permissionGranted -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Izin kamera diperlukan\nuntuk ball tracking",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) { Text("Beri Izin Kamera", color = Color.White) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) {
                        Text("Kembali", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            else -> {
                val detections by viewModel.detections.collectAsState()
                val calibrationState by viewModel.calibrationState.collectAsState()
                val trackedBall by viewModel.trackedBall.collectAsState()
                val previewView = remember { PreviewView(context) }

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    detections.forEach { detection ->
                        val left   = detection.boundingBox.left   * size.width
                        val top    = detection.boundingBox.top    * size.height
                        val right  = detection.boundingBox.right  * size.width
                        val bottom = detection.boundingBox.bottom * size.height
                        drawRect(
                            color = CyanAccent,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${(detection.confidence * 100).toInt()}%",
                            left,
                            top - 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.CYAN
                                textSize = 12.sp.toPx()
                            }
                        )
                    }

                    // Trail
                    trackedBall?.trajectory?.zipWithNext()?.forEach { (a, b) ->
                        drawLine(
                            color = CyanAccent.copy(alpha = 0.5f),
                            start = Offset(a.x * size.width, a.y * size.height),
                            end   = Offset(b.x * size.width, b.y * size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    // Crosshair
                    trackedBall?.let { ball ->
                        val cx = ball.position.x * size.width
                        val cy = ball.position.y * size.height
                        val color = if (ball.isPredicted) Color.Yellow else CyanAccent
                        drawCircle(color = color, radius = 6.dp.toPx(), center = Offset(cx, cy))
                    }
                }

                when (calibrationState) {
                    is CalibrationState.Uncalibrated,
                    is CalibrationState.Calibrating -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CyanAccent)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Mendeteksi lapangan...",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    is CalibrationState.Failed -> {
                        Text(
                            text = "Kalibrasi gagal — koordinat lapangan tidak tersedia",
                            color = Color.Yellow,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    is CalibrationState.Calibrated -> { /* no overlay — ball detection active */ }
                }

                LaunchedEffect(lifecycleOwner) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(AndroidSize(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also {
                                    it.setAnalyzer(viewModel.cameraExecutor, viewModel.imageAnalyzer)
                                }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            viewModel.initCalibration(context.applicationContext)
                        } catch (e: Exception) {
                            viewModel.onCameraError("Kamera tidak dapat dibuka: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                Text(
                    text = "Camera ready",
                    color = CyanAccent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
                ) {
                    Text("← Kembali", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
