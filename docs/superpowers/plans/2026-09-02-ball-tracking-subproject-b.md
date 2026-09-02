# Ball Tracking Sub-project B: TFLite Ball Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-frame tennis ball detection using EfficientDet-Lite0 TFLite (COCO) and show bounding box overlay on CameraScreen.

**Architecture:** `BallDetector` implements the existing `FrameAnalyzer` fun interface from Sub-project A, runs TFLite Task Library `ObjectDetector` on each `ImageProxy` frame, filters "sports ball" class, and emits normalized `List<Detection>` via callback into `BallTrackingViewModel.detections: StateFlow`. `CameraScreen` observes this flow and draws a Compose Canvas bounding box overlay on top of the camera preview.

**Tech Stack:** `tensorflow-lite-task-vision:0.4.4`, EfficientDet-Lite0 COCO model (~4 MB bundled in assets), Jetpack Compose Canvas

## Global Constraints

- Min SDK 24, offline only
- `tensorflow-lite-task-vision` version exactly `0.4.4` — no GPU delegate
- Model file: `app/src/main/assets/efficientdet_lite0.tflite` (exact name)
- `BallDetector` companion constants: `SCORE_THRESHOLD = 0.4f`, `MAX_RESULTS = 5`, `BALL_LABEL = "sports ball"`, `MODEL_FILE = "efficientdet_lite0.tflite"` — no magic numbers
- `Detection.boundingBox` is normalized 0-1 (`RectF`), not pixel coordinates
- `ImageProxy` must be closed immediately after `image.toBitmap()`, before inference
- `BallDetector.close()` called from `BallTrackingViewModel.onCleared()` before `super.onCleared()`
- `initDetector(context: Context)` is idempotent — no-op if `ballDetector != null`
- No inline `Color(0x...)` Compose literals — use `CyanAccent` from `ui/theme/Color.kt` for Compose drawing
- YAGNI — no runtime threshold slider, no GPU delegate, no multi-class detection UI

---

### Task 1: TFLite Task Library Dependency + Model Asset

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Add: `app/src/main/assets/efficientdet_lite0.tflite`

**Interfaces:**
- Produces: `libs.tflite.task.vision` catalog alias available for later tasks

- [ ] **Step 1: Add version + library entry to libs.versions.toml**

Open `gradle/libs.versions.toml`. Add one line under `[versions]` and one under `[libraries]`:

```toml
[versions]
# existing entries ...
tflite-task-vision = "0.4.4"

[libraries]
# existing entries ...
tflite-task-vision = { group = "org.tensorflow", name = "tensorflow-lite-task-vision", version.ref = "tflite-task-vision" }
```

- [ ] **Step 2: Add implementation dependency to app/build.gradle.kts**

Open `app/build.gradle.kts`. Inside the `dependencies { }` block, add:

```kotlin
implementation(libs.tflite.task.vision)
```

- [ ] **Step 3: Create assets directory and download model**

Run in PowerShell from project root (`C:\Users\1882\AndroidStudioProjects\TennisScorer`):

```powershell
New-Item -ItemType Directory -Force -Path "app\src\main\assets"
Invoke-WebRequest `
  -Uri "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite" `
  -OutFile "app\src\main\assets\efficientdet_lite0.tflite"
```

Expected: file `app\src\main\assets\efficientdet_lite0.tflite` exists, size ~4 MB.

Verify:
```powershell
(Get-Item "app\src\main\assets\efficientdet_lite0.tflite").Length / 1MB
```
Expected: output between 3 and 6 (MB).

- [ ] **Step 4: Verify build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/assets/efficientdet_lite0.tflite
git commit -m "feat(tracking): add tflite-task-vision 0.4.4 dep and EfficientDet-Lite0 model asset"
```

---

### Task 2: Detection Data Class + BallDetector

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/tracking/Detection.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/BallDetector.kt`

**Interfaces:**
- Consumes:
  - `FrameAnalyzer` fun interface (existing): `fun analyze(image: ImageProxy)`
  - `libs.tflite.task.vision` from Task 1
- Produces:
  - `data class Detection(val boundingBox: RectF, val confidence: Float)` — normalized 0-1
  - `class BallDetector(context: Context, onDetections: (List<Detection>) -> Unit) : FrameAnalyzer`
  - `fun BallDetector.close()` — closes `ObjectDetector`, called from ViewModel

*Note: `BallDetector` cannot be unit-tested in JVM — `ObjectDetector.createFromFileAndOptions()` requires Android assets context and a real `.tflite` file. Verification for this task is compile-only (`assembleDebug`). Runtime behavior is verified via manual smoke test in Task 3.*

- [ ] **Step 1: Create Detection.kt**

Create `app/src/main/java/com/example/tennisscorer/tracking/Detection.kt`:

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.RectF

data class Detection(
    val boundingBox: RectF,
    val confidence: Float
)
```

- [ ] **Step 2: Create BallDetector.kt**

Create `app/src/main/java/com/example/tennisscorer/tracking/BallDetector.kt`:

```kotlin
package com.example.tennisscorer.tracking

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class BallDetector(
    context: Context,
    private val onDetections: (List<Detection>) -> Unit
) : FrameAnalyzer {

    private val objectDetector: ObjectDetector = ObjectDetector.createFromFileAndOptions(
        context,
        MODEL_FILE,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(SCORE_THRESHOLD)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        image.close()
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = objectDetector.detect(tensorImage)
        val detections = results
            .filter { result -> result.categories.any { it.label == BALL_LABEL } }
            .map { result ->
                val box = result.boundingBox
                val bestCategory = result.categories.first { it.label == BALL_LABEL }
                Detection(
                    boundingBox = RectF(
                        box.left / bitmap.width,
                        box.top / bitmap.height,
                        box.right / bitmap.width,
                        box.bottom / bitmap.height
                    ),
                    confidence = bestCategory.score
                )
            }
        onDetections(detections)
    }

    fun close() {
        objectDetector.close()
    }

    companion object {
        private const val MODEL_FILE = "efficientdet_lite0.tflite"
        private const val SCORE_THRESHOLD = 0.4f
        private const val MAX_RESULTS = 5
        private const val BALL_LABEL = "sports ball"
    }
}
```

- [ ] **Step 3: Verify build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. No unused import warnings.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/example/tennisscorer/tracking/Detection.kt app/src/main/java/com/example/tennisscorer/tracking/BallDetector.kt
git commit -m "feat(tracking): add Detection data class and BallDetector TFLite FrameAnalyzer"
```

---

### Task 3: BallTrackingViewModel Update + CameraScreen Overlay

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `Detection` from Task 2
  - `BallDetector(context, onDetections)` from Task 2
  - `BallTrackingViewModel.setFrameAnalyzer(analyzer: FrameAnalyzer)` (existing)
  - `BallTrackingViewModel.cameraExecutor`, `viewModel.imageAnalyzer` (existing)
- Produces:
  - `BallTrackingViewModel.detections: StateFlow<List<Detection>>`
  - `BallTrackingViewModel.initDetector(context: Context)` — idempotent
  - Canvas bounding box overlay in `CameraScreen`

- [ ] **Step 1: Write the failing test**

Open `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`.

Add one test at the end of the class (before the closing `}`):

```kotlin
@Test fun `detections starts empty`() {
    assertTrue(vm.detections.value.isEmpty())
}
```

The file now ends with:

```kotlin
    @Test fun `cameraExecutor not null`() {
        assertNotNull(vm.cameraExecutor)
    }

    @Test fun `detections starts empty`() {
        assertTrue(vm.detections.value.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
.\gradlew test --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest.detections starts empty"
```

Expected: `FAILED` — `Unresolved reference: detections`

- [ ] **Step 3: Update BallTrackingViewModel**

Open `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`.

Replace the entire file with:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

```
.\gradlew test --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest.detections starts empty"
```

Expected: `PASS`

- [ ] **Step 5: Run full test suite**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, all 27 tests pass (26 existing + 1 new).

- [ ] **Step 6: Update CameraScreen.kt**

Open `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`.

Replace the entire file with:

```kotlin
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.ui.theme.ActionBtnBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.viewmodels.BallTrackingViewModel

@Composable
fun CameraScreen(
    viewModel: BallTrackingViewModel = viewModel(),
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
                            viewModel.initDetector(context.applicationContext)
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
```

Key changes vs. Sub-project A version:
- `import android.util.Size as AndroidSize` (alias to avoid conflict with Compose `Size`)
- `import androidx.compose.foundation.Canvas`
- `import androidx.compose.ui.geometry.Offset`
- `import androidx.compose.ui.geometry.Size`
- `import androidx.compose.ui.graphics.drawscope.Stroke`
- `val detections by viewModel.detections.collectAsState()` in `else` branch
- `Canvas` overlay between `AndroidView` and `LaunchedEffect`
- `viewModel.initDetector(context.applicationContext)` inside try block after `bindToLifecycle`
- `setTargetResolution(AndroidSize(640, 480))` (was `Size(640, 480)`)

- [ ] **Step 7: Verify build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. One deprecation warning on `setTargetResolution` is acceptable (pre-existing).

- [ ] **Step 8: Manual smoke test on device**

Install and launch the app:
```
.\gradlew installDebug
```

Test sequence:
1. SplashScreen → tap "Ball Tracking" → permission dialog appears
2. Grant permission → camera preview opens landscape, "Camera ready" text visible
3. Aim camera at a sports ball (tennis ball, football, etc.) → cyan bounding box appears around the ball with confidence percentage (e.g., "73%")
4. Move the ball → bounding box follows
5. Remove ball from frame → bounding box disappears
6. Tap "← Kembali" → returns to SplashScreen, no crash
7. Tap "Start" → PlayerInput screen → Game screen (manual scoring still works, unaffected)

- [ ] **Step 9: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
git commit -m "feat(tracking): add detections StateFlow, initDetector, and CameraScreen bounding box overlay"
```
