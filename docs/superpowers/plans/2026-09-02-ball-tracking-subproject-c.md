# Ball Tracking Sub-project C: Homography Court Calibration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tambahkan kalibrasi lapangan otomatis ke pipeline CameraX menggunakan OpenCV (Canny + HoughLinesP), hasilkan `HomographyMapper` untuk konversi piksel → meter, dan tampilkan status kalibrasi di `CameraScreen`.

**Architecture:** `CourtDetector` menggantikan `BallDetector` sementara sebagai FrameAnalyzer saat CameraScreen buka. Setelah court terdeteksi (atau gagal setelah 60 frame), ViewModel swap kembali ke `BallDetector`. `HomographyMapper` adalah pure Kotlin (tidak ada OpenCV runtime) sehingga JVM-testable. `BallTrackingViewModel` mengekspos `calibrationState: StateFlow<CalibrationState>` yang dipakai `CameraScreen` untuk tampilkan overlay.

**Tech Stack:** OpenCV 4.9.0 (Maven Central), Kotlin, Jetpack Compose, CameraX 1.4.1 (existing), MockK 1.13.8 (existing)

## Global Constraints

- Min SDK 24
- Offline — tidak ada network call
- OpenCV `4.9.0` via Maven Central: `org.opencv:opencv:4.9.0`
- `HomographyMapper` tidak boleh mengimport OpenCV — pure Kotlin math
- Semua OpenCV `Mat` wajib di-`release()` di `tryDetectCourt()` baik di happy path maupun di setiap early return (gunakan `finally` block)
- `CourtDetector.done` flag wajib mencegah pemrosesan frame setelah callback dipanggil
- `MAX_FRAMES = 60` harus konstanta di `CourtDetector.Companion`, bukan magic number
- `COURT_WIDTH_M = 10.97f`, `COURT_LENGTH_M = 23.77f` harus konstanta di `HomographyMapper.Companion`
- `initCalibration` idempotent: jika `_calibrationState.value != CalibrationState.Uncalibrated`, langsung return
- Tidak ada inline `Color(0x...)` — gunakan `CyanAccent` dari theme
- Tidak ada re-kalibrasi manual, court outline overlay, GPU OpenCV delegate (YAGNI)
- `onCleared()`: `courtDetector?.close()` sebelum `ballDetector?.close()` sebelum `super.onCleared()` sebelum `cameraExecutor.shutdown()`

---

### Task 1: OpenCV dependency + HomographyMapper + data types

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/com/example/tennisscorer/tracking/HomographyMapperTest.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/HomographyMapper.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/HomographyResult.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/CalibrationState.kt`

**Interfaces:**
- Produces:
  - `HomographyMapper(matrix: FloatArray)` — constructor
  - `HomographyMapper.mapToCourtCoords(normalizedPoint: PointF): PointF`
  - `HomographyMapper.mapNormalized(nx: Float, ny: Float): Pair<Float, Float>` (internal)
  - `HomographyMapper.COURT_WIDTH_M: Float = 10.97f`
  - `HomographyMapper.COURT_LENGTH_M: Float = 23.77f`
  - `HomographyResult.Success(matrix: FloatArray)` sealed class
  - `HomographyResult.Failed(reason: String)` sealed class
  - `CalibrationState.Uncalibrated` sealed class
  - `CalibrationState.Calibrating` sealed class
  - `CalibrationState.Calibrated(mapper: HomographyMapper)` sealed class
  - `CalibrationState.Failed(reason: String)` sealed class

- [ ] **Step 1: Tambah OpenCV ke libs.versions.toml**

Buka `gradle/libs.versions.toml`. Tambahkan `opencv = "4.9.0"` di akhir blok `[versions]` dan tambahkan entry library di blok `[libraries]`:

```toml
[versions]
agp = "9.3.2"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
navigationCompose = "2.8.5"
ksp = "2.2.10-2.0.2"
room = "2.7.1"
camerax = "1.4.1"
mockk   = "1.13.8"
tflite-task-vision = "0.4.4"
opencv = "4.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
camerax-core     = { group = "androidx.camera", name = "camera-core",     version.ref = "camerax" }
camerax-camera2  = { group = "androidx.camera", name = "camera-camera2",  version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view     = { group = "androidx.camera", name = "camera-view",     version.ref = "camerax" }
mockk            = { group = "io.mockk",        name = "mockk",           version.ref = "mockk"   }
tflite-task-vision = { group = "org.tensorflow", name = "tensorflow-lite-task-vision", version.ref = "tflite-task-vision" }
opencv           = { group = "org.opencv",       name = "opencv",          version.ref = "opencv"  }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Tambah OpenCV ke app/build.gradle.kts**

Buka `app/build.gradle.kts`. Tambahkan `implementation(libs.opencv)` setelah `implementation(libs.tflite.task.vision)`:

```kotlin
    implementation(libs.tflite.task.vision)
    implementation(libs.opencv)
```

- [ ] **Step 3: Tulis HomographyMapperTest (3 tests — akan compile-error dulu)**

Buat file baru `app/src/test/java/com/example/tennisscorer/tracking/HomographyMapperTest.kt`:

```kotlin
package com.example.tennisscorer.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class HomographyMapperTest {

    @Test fun `identity matrix maps point to itself`() {
        val identity = floatArrayOf(1f, 0f, 0f,  0f, 1f, 0f,  0f, 0f, 1f)
        val mapper = HomographyMapper(identity)
        val (x, y) = mapper.mapNormalized(0.3f, 0.7f)
        assertEquals(0.3f, x, 0.001f)
        assertEquals(0.7f, y, 0.001f)
    }

    @Test fun `scaling matrix maps unit corner to court dimensions`() {
        val matrix = floatArrayOf(
            HomographyMapper.COURT_WIDTH_M,  0f, 0f,
            0f, HomographyMapper.COURT_LENGTH_M, 0f,
            0f, 0f, 1f
        )
        val mapper = HomographyMapper(matrix)
        val (x, y) = mapper.mapNormalized(1f, 1f)
        assertEquals(HomographyMapper.COURT_WIDTH_M, x, 0.001f)
        assertEquals(HomographyMapper.COURT_LENGTH_M, y, 0.001f)
    }

    @Test fun `constants match standard doubles court dimensions`() {
        assertEquals(10.97f, HomographyMapper.COURT_WIDTH_M, 0.001f)
        assertEquals(23.77f, HomographyMapper.COURT_LENGTH_M, 0.001f)
    }
}
```

- [ ] **Step 4: Jalankan test untuk verifikasi compile-error**

```
.\gradlew test --tests "com.example.tennisscorer.tracking.HomographyMapperTest"
```

Expected: `BUILD FAILED` — `Unresolved reference: HomographyMapper`

- [ ] **Step 5: Buat HomographyMapper.kt**

Buat file baru `app/src/main/java/com/example/tennisscorer/tracking/HomographyMapper.kt`:

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.PointF

class HomographyMapper(private val matrix: FloatArray) {
    // matrix: FloatArray(9), 3×3 row-major homography (from OpenCV getPerspectiveTransform)

    fun mapToCourtCoords(normalizedPoint: PointF): PointF {
        val (x, y) = mapNormalized(normalizedPoint.x, normalizedPoint.y)
        return PointF(x, y)
    }

    internal fun mapNormalized(nx: Float, ny: Float): Pair<Float, Float> {
        val w = matrix[6] * nx + matrix[7] * ny + matrix[8]
        return Pair(
            (matrix[0] * nx + matrix[1] * ny + matrix[2]) / w,
            (matrix[3] * nx + matrix[4] * ny + matrix[5]) / w
        )
    }

    companion object {
        const val COURT_WIDTH_M = 10.97f    // doubles sideline to sideline
        const val COURT_LENGTH_M = 23.77f   // baseline to baseline
    }
}
```

- [ ] **Step 6: Jalankan test untuk verifikasi 3 test pass**

```
.\gradlew test --tests "com.example.tennisscorer.tracking.HomographyMapperTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 7: Buat HomographyResult.kt**

Buat file baru `app/src/main/java/com/example/tennisscorer/tracking/HomographyResult.kt`:

```kotlin
package com.example.tennisscorer.tracking

sealed class HomographyResult {
    data class Success(val matrix: FloatArray) : HomographyResult()
    data class Failed(val reason: String) : HomographyResult()
}
```

- [ ] **Step 8: Buat CalibrationState.kt**

Buat file baru `app/src/main/java/com/example/tennisscorer/tracking/CalibrationState.kt`:

```kotlin
package com.example.tennisscorer.tracking

sealed class CalibrationState {
    object Uncalibrated : CalibrationState()
    object Calibrating : CalibrationState()
    data class Calibrated(val mapper: HomographyMapper) : CalibrationState()
    data class Failed(val reason: String) : CalibrationState()
}
```

- [ ] **Step 9: Jalankan full test suite**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 21 tests pass (18 existing + 3 baru dari HomographyMapperTest).

- [ ] **Step 10: Verifikasi build**

```
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Satu deprecation warning `setTargetResolution` di CameraScreen adalah pre-existing dan acceptable.

- [ ] **Step 11: Commit**

```
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/tennisscorer/tracking/HomographyMapper.kt app/src/main/java/com/example/tennisscorer/tracking/HomographyResult.kt app/src/main/java/com/example/tennisscorer/tracking/CalibrationState.kt app/src/test/java/com/example/tennisscorer/tracking/HomographyMapperTest.kt
git commit -m "feat(tracking): add opencv dep, HomographyMapper, CalibrationState, HomographyResult"
```

---

### Task 2: CourtDetector

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/tracking/CourtDetector.kt`

**Interfaces:**
- Consumes:
  - `FrameAnalyzer` fun interface (existing) — `fun analyze(image: ImageProxy)`
  - `HomographyResult.Success(matrix: FloatArray)` dan `HomographyResult.Failed(reason: String)` dari Task 1
  - `HomographyMapper.COURT_WIDTH_M` dan `HomographyMapper.COURT_LENGTH_M` dari Task 1
- Produces:
  - `CourtDetector(onResult: (HomographyResult) -> Unit)` — constructor
  - `CourtDetector.analyze(image: ImageProxy)` — implements FrameAnalyzer
  - `CourtDetector.close()` — safe to call from main thread
  - `CourtDetector.MAX_FRAMES: Int = 60` (companion)

Tidak ada JVM unit test untuk `CourtDetector` — OpenCV dan ImageProxy tidak bisa diinstansiasi di JVM tanpa Android runtime. Coverage dari manual smoke test.

- [ ] **Step 1: Buat CourtDetector.kt**

Buat file baru `app/src/main/java/com/example/tennisscorer/tracking/CourtDetector.kt`:

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2

class CourtDetector(
    private val onResult: (HomographyResult) -> Unit
) : FrameAnalyzer {

    companion object {
        const val MAX_FRAMES = 60
        private const val CANNY_LOW = 50.0
        private const val CANNY_HIGH = 150.0
        private const val HOUGH_THRESHOLD = 80
        private const val HOUGH_MIN_LINE_LENGTH = 100.0
        private const val HOUGH_MAX_LINE_GAP = 10.0
        private const val ANGLE_TOLERANCE_DEG = 20.0
        private const val MIN_QUAD_AREA_RATIO = 0.15
    }

    private var framesProcessed = 0
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) { image.close(); return }
        val bitmap = image.toBitmap()
        image.close()
        framesProcessed++
        val matrix = tryDetectCourt(bitmap)
        when {
            matrix != null -> {
                done = true
                onResult(HomographyResult.Success(matrix))
            }
            framesProcessed >= MAX_FRAMES -> {
                done = true
                onResult(HomographyResult.Failed("Lapangan tidak terdeteksi setelah $MAX_FRAMES frame"))
            }
        }
    }

    fun close() { /* Mat released per-frame in tryDetectCourt — no persistent state */ }

    private fun tryDetectCourt(bitmap: Bitmap): FloatArray? {
        val rgbaMat = Mat(); val grayMat = Mat(); val edgesMat = Mat(); val linesMat = Mat()
        var H: Mat? = null; var src: MatOfPoint2f? = null; var dst: MatOfPoint2f? = null
        return try {
            Utils.bitmapToMat(bitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Canny(grayMat, edgesMat, CANNY_LOW, CANNY_HIGH)
            Imgproc.HoughLinesP(
                edgesMat, linesMat, 1.0, Math.PI / 180.0,
                HOUGH_THRESHOLD, HOUGH_MIN_LINE_LENGTH, HOUGH_MAX_LINE_GAP
            )

            val horizontals = mutableListOf<DoubleArray>()
            val verticals   = mutableListOf<DoubleArray>()
            for (i in 0 until linesMat.rows()) {
                val seg = linesMat.get(i, 0)
                val dx = seg[2] - seg[0]; val dy = seg[3] - seg[1]
                val angleDeg = Math.toDegrees(atan2(abs(dy), abs(dx)))
                when {
                    angleDeg < ANGLE_TOLERANCE_DEG -> horizontals.add(seg)
                    angleDeg > (90.0 - ANGLE_TOLERANCE_DEG) -> verticals.add(seg)
                }
            }
            if (horizontals.size < 2 || verticals.size < 2) return null

            // Near baseline = larger avg-Y (lower in image); Far = smaller avg-Y
            // Left sideline = smaller avg-X; Right sideline = larger avg-X
            val sortedH = horizontals.sortedByDescending { (it[1] + it[3]) / 2.0 }
            val sortedV = verticals.sortedBy { (it[0] + it[2]) / 2.0 }
            val hNear = sortedH[0]; val hFar = sortedH[1]
            val vLeft = sortedV[0]; val vRight = sortedV[1]

            val bl = intersect(vLeft, hNear)  ?: return null  // near-left  → (0, 0)
            val br = intersect(vRight, hNear) ?: return null  // near-right → (WIDTH, 0)
            val tl = intersect(vLeft, hFar)   ?: return null  // far-left   → (0, LENGTH)
            val tr = intersect(vRight, hFar)  ?: return null  // far-right  → (WIDTH, LENGTH)

            if (shoelaceArea(listOf(bl, br, tr, tl)) <
                MIN_QUAD_AREA_RATIO * bitmap.width * bitmap.height) return null

            src = MatOfPoint2f(
                Point(bl.x.toDouble(), bl.y.toDouble()),
                Point(br.x.toDouble(), br.y.toDouble()),
                Point(tl.x.toDouble(), tl.y.toDouble()),
                Point(tr.x.toDouble(), tr.y.toDouble())
            )
            dst = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(HomographyMapper.COURT_WIDTH_M.toDouble(), 0.0),
                Point(0.0, HomographyMapper.COURT_LENGTH_M.toDouble()),
                Point(HomographyMapper.COURT_WIDTH_M.toDouble(), HomographyMapper.COURT_LENGTH_M.toDouble())
            )
            H = Imgproc.getPerspectiveTransform(src, dst)
            FloatArray(9) { i -> H.get(i / 3, i % 3)[0].toFloat() }
        } catch (_: Exception) {
            null
        } finally {
            rgbaMat.release(); grayMat.release(); edgesMat.release(); linesMat.release()
            H?.release(); src?.release(); dst?.release()
        }
    }

    private fun intersect(s1: DoubleArray, s2: DoubleArray): PointF? {
        val x1 = s1[0]; val y1 = s1[1]; val x2 = s1[2]; val y2 = s1[3]
        val x3 = s2[0]; val y3 = s2[1]; val x4 = s2[2]; val y4 = s2[3]
        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-10) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
        return PointF((x1 + t * (x2 - x1)).toFloat(), (y1 + t * (y2 - y1)).toFloat())
    }

    private fun shoelaceArea(pts: List<PointF>): Double {
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }
}
```

- [ ] **Step 2: Verifikasi build**

```
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Jalankan full test suite untuk verifikasi tidak ada regresi**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 21 tests pass (tidak berubah dari Task 1 — CourtDetector tidak punya JVM tests).

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/example/tennisscorer/tracking/CourtDetector.kt
git commit -m "feat(tracking): add CourtDetector with Canny+HoughLinesP court detection and homography"
```

---

### Task 3: BallTrackingViewModel — calibration integration

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `CourtDetector(onResult: (HomographyResult) -> Unit)` dari Task 2
  - `HomographyMapper(matrix: FloatArray)` dari Task 1
  - `CalibrationState.*` dari Task 1
  - `HomographyResult.Success`, `HomographyResult.Failed` dari Task 1
- Produces:
  - `BallTrackingViewModel.calibrationState: StateFlow<CalibrationState>`
  - `BallTrackingViewModel.initCalibration(context: Context)` — idempotent; menggantikan panggilan `initDetector` dari CameraScreen

- [ ] **Step 1: Tulis 3 failing tests di BallTrackingViewModelTest**

Buka `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`. Tambahkan import baru dan 3 test baru di akhir class (sebelum `}`):

```kotlin
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
```

- [ ] **Step 2: Jalankan test untuk verifikasi gagal**

```
.\gradlew test --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: `BUILD FAILED` — `Unresolved reference: calibrationState` dan `Unresolved reference: initCalibration`.

- [ ] **Step 3: Replace BallTrackingViewModel.kt**

Replace seluruh isi `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`:

```kotlin
package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.tennisscorer.tracking.BallDetector
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.CourtDetector
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.FrameAnalyzer
import com.example.tennisscorer.tracking.HomographyMapper
import com.example.tennisscorer.tracking.HomographyResult
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

    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Uncalibrated)
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

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

    fun initDetector(context: Context) {
        if (ballDetector != null) return
        val detector = BallDetector(context.applicationContext) { _detections.value = it }
        ballDetector = detector
        setFrameAnalyzer(detector)
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
        courtDetector?.close()
        ballDetector?.close()
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
```

- [ ] **Step 4: Jalankan test untuk verifikasi 10 tests pass**

```
.\gradlew test --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, 10 tests pass (7 existing + 3 baru).

- [ ] **Step 5: Jalankan full test suite**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 24 tests pass (21 dari Task 1 + 3 baru).

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
git commit -m "feat(tracking): add calibrationState StateFlow and initCalibration to BallTrackingViewModel"
```

---

### Task 4: CameraScreen — calibration overlay

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`

**Interfaces:**
- Consumes:
  - `BallTrackingViewModel.calibrationState: StateFlow<CalibrationState>` dari Task 3
  - `BallTrackingViewModel.initCalibration(context: Context)` dari Task 3
  - `CalibrationState.Uncalibrated`, `CalibrationState.Calibrating`, `CalibrationState.Calibrated`, `CalibrationState.Failed` dari Task 1
- Produces:
  - `CameraScreen` menampilkan spinner "Mendeteksi lapangan..." saat kalibrasi berjalan
  - `CameraScreen` menampilkan teks kuning saat kalibrasi gagal
  - `CameraScreen` tidak menampilkan overlay saat kalibrasi berhasil (ball detection aktif)

Tidak ada JVM unit test untuk perubahan UI — verifikasi via `assembleDebug` + manual smoke test.

- [ ] **Step 1: Replace CameraScreen.kt**

Replace seluruh isi `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`:

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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.tracking.CalibrationState
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
                val calibrationState by viewModel.calibrationState.collectAsState()
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
```

Perubahan dari versi Sub-proyek B:
- Tambah import `com.example.tennisscorer.tracking.CalibrationState`
- Tambah `val calibrationState by viewModel.calibrationState.collectAsState()` di `else` branch
- Ganti `viewModel.initDetector(...)` → `viewModel.initCalibration(...)` di try block
- Tambah `when (calibrationState)` overlay setelah Canvas bounding box

- [ ] **Step 2: Verifikasi build**

```
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Deprecation warning `setTargetResolution` adalah pre-existing, acceptable.

- [ ] **Step 3: Jalankan full test suite untuk verifikasi tidak ada regresi**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 24 tests pass.

- [ ] **Step 4: Manual smoke test**

Install ke device:
```
.\gradlew installDebug
```

Test sequence:
1. SplashScreen → tap "Ball Tracking" → izin kamera (jika belum) → CameraScreen terbuka
2. Overlay semi-transparan hitam + `CircularProgressIndicator` CyanAccent + teks putih "Mendeteksi lapangan..." muncul
3. **Scenario A — lapangan terdeteksi:** Arahkan ke lapangan tenis dari samping → dalam ~2 detik (60 frame) overlay menghilang, bounding box bola (CyanAccent) muncul saat bola terlihat
4. **Scenario B — gagal:** Arahkan ke tembok/lantai → setelah ~2 detik overlay menghilang, muncul teks kuning "Kalibrasi gagal — koordinat lapangan tidak tersedia", bounding box bola tetap berfungsi
5. Navigasi kembali → tidak ada crash, kamera berhenti
6. Tap "Start" → PlayerInput → ScoreboardScreen (manual scoring tetap berfungsi, tidak terpengaruh)

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt
git commit -m "feat(ui): add calibration overlay to CameraScreen — spinner during detection, warning on failure"
```
