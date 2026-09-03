# Ball Tracking Sub-project D: Kalman Filter Trajectory Tracking — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tambahkan `KalmanTracker` (pure Kotlin, 4-state) sebagai post-processing di `BallTrackingViewModel` untuk menghaluskan output `BallDetector`, menyimpan trajektori seluruh rally, dan menampilkan trail + crosshair di `CameraScreen`.

**Architecture:** `BallDetector` callback memanggil `kalmanTracker.update(detections.firstOrNull())` di camera executor thread. Hasilnya disimpan di `StateFlow<TrackedBall?>` yang dikoleksi `CameraScreen`. `KalmanTracker` tidak mengimpor CameraX/OpenCV — pure Kotlin math dengan helper matriks flat `FloatArray`.

**Tech Stack:** Kotlin, Jetpack Compose, MockK 1.13.8 (existing), JUnit 4 (existing)

## Global Constraints

- `KalmanTracker` tidak boleh mengimpor CameraX atau OpenCV — pure Kotlin, tidak ada framework berat
- `PROCESS_NOISE_Q = 1e-4f`, `MEASUREMENT_NOISE_R = 1e-2f` harus konstanta di `KalmanTracker.Companion`
- `trajectory` di `TrackedBall` adalah snapshot immutable (`_trajectory.toList()`) — KalmanTracker menjaga internal `MutableList<PointF>`
- `reset()` idempotent
- Tidak ada batas panjang trajektori — simpan seluruh rally
- Tidak ada inline `Color(0x...)` — gunakan `CyanAccent` dan `Color.Yellow` dari theme
- YAGNI: tidak ada akselerasi state, tidak ada multi-ball tracking, tidak ada persistensi ke disk
- `android.testOptions.unitTests.isReturnDefaultValues = true` diperlukan agar `RectF` dapat diinstansiasi di JVM unit tests

---

### Task 1: TrackedBall + KalmanTracker (TDD)

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/TrackedBall.kt`
- Create: `app/src/test/java/com/example/tennisscorer/tracking/KalmanTrackerTest.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/KalmanTracker.kt`

**Interfaces:**
- Consumes:
  - `Detection(boundingBox: RectF, confidence: Float)` — existing
- Produces:
  - `TrackedBall(position: PointF, velocity: PointF, isPredicted: Boolean, trajectory: List<PointF>)` — data class
  - `KalmanTracker()` — constructor
  - `KalmanTracker.update(detection: Detection?): TrackedBall?` — null sebelum init pertama
  - `KalmanTracker.reset()` — idempotent, bersihkan state dan trajectory
  - `KalmanTracker.PROCESS_NOISE_Q: Float = 1e-4f`
  - `KalmanTracker.MEASUREMENT_NOISE_R: Float = 1e-2f`

- [ ] **Step 1: Tambah `isReturnDefaultValues = true` ke testOptions di app/build.gradle.kts**

Buka `app/build.gradle.kts`. Tambahkan blok `testOptions` di dalam blok `android { }`, setelah `buildFeatures { }`:

```kotlin
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
```

- [ ] **Step 2: Buat TrackedBall.kt**

Buat file baru `app/src/main/java/com/example/tennisscorer/tracking/TrackedBall.kt`:

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.PointF

data class TrackedBall(
    val position: PointF,
    val velocity: PointF,
    val isPredicted: Boolean,
    val trajectory: List<PointF>
)
```

- [ ] **Step 3: Buat KalmanTrackerTest.kt (6 test — compile-error dulu)**

Buat file baru `app/src/test/java/com/example/tennisscorer/tracking/KalmanTrackerTest.kt`:

```kotlin
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
```

- [ ] **Step 4: Jalankan test untuk verifikasi compile-error**

```
.\gradlew test --tests "com.example.tennisscorer.tracking.KalmanTrackerTest"
```

Expected: `BUILD FAILED` — `Unresolved reference: KalmanTracker`

- [ ] **Step 5: Buat KalmanTracker.kt**

Buat file baru `app/src/main/java/com/example/tennisscorer/tracking/KalmanTracker.kt`:

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.PointF

class KalmanTracker {

    companion object {
        const val PROCESS_NOISE_Q = 1e-4f
        const val MEASUREMENT_NOISE_R = 1e-2f

        // F: 4x4 state transition matrix, row-major
        // [1,0,1,0]  cx  += vx
        // [0,1,0,1]  cy  += vy
        // [0,0,1,0]  vx  unchanged
        // [0,0,0,1]  vy  unchanged
        private val F = floatArrayOf(
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        private val FT = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
        )
        // H: 2x4 observation matrix — observe cx and cy only
        private val H = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f
        )
        // HT: 4x2 transpose of H
        private val HT = floatArrayOf(
            1f, 0f,
            0f, 1f,
            0f, 0f,
            0f, 0f
        )
    }

    private var x = FloatArray(4)   // state [cx, cy, vx, vy]
    private var P = FloatArray(16)  // covariance 4x4, row-major
    private var initialized = false
    private val _trajectory = mutableListOf<PointF>()

    fun update(detection: Detection?): TrackedBall? {
        if (!initialized) {
            if (detection == null) return null
            val cx = (detection.boundingBox.left + detection.boundingBox.right) / 2f
            val cy = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f
            x = floatArrayOf(cx, cy, 0f, 0f)
            P = floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
            initialized = true
            _trajectory.add(PointF(cx, cy))
            return TrackedBall(PointF(x[0], x[1]), PointF(x[2], x[3]), false, _trajectory.toList())
        }

        // Predict: x̂ = F·x,  P̂ = F·P·Fᵀ + Q·I
        val xPred = m4v4(F, x)
        val pPred = m4m4addQ(m4m4(m4m4(F, P), FT))

        val isPredicted = detection == null
        if (!isPredicted) {
            val cx = (detection!!.boundingBox.left + detection.boundingBox.right) / 2f
            val cy = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f

            // innovation: y = z - H·x̂  (H selects first 2 elements)
            val y = floatArrayOf(cx - xPred[0], cy - xPred[1])

            // S = H·P̂·Hᵀ + R·I₂  (H picks rows 0,1 of P̂; Hᵀ picks cols 0,1 → S = P̂[0..1, 0..1] + R·I₂)
            val s00 = pPred[0] + MEASUREMENT_NOISE_R
            val s01 = pPred[1]
            val s10 = pPred[4]
            val s11 = pPred[5] + MEASUREMENT_NOISE_R
            val det = s00 * s11 - s01 * s10
            val sInv = floatArrayOf(s11 / det, -s01 / det, -s10 / det, s00 / det)

            // K = P̂·Hᵀ·S⁻¹  (P̂·Hᵀ picks cols 0,1 of P̂)
            val pHt = m4x4m4x2(pPred, HT)
            val K = m4x2m2x2(pHt, sInv)

            // x = x̂ + K·y
            x = FloatArray(4) { i -> xPred[i] + K[i * 2] * y[0] + K[i * 2 + 1] * y[1] }

            // P = (I - K·H)·P̂
            val KH = m4x2m2x4(K, H)
            val IKH = FloatArray(16) { i -> (if (i % 5 == 0) 1f else 0f) - KH[i] }
            P = m4m4(IKH, pPred)
        } else {
            x = xPred
            P = pPred
        }

        _trajectory.add(PointF(x[0], x[1]))
        return TrackedBall(PointF(x[0], x[1]), PointF(x[2], x[3]), isPredicted, _trajectory.toList())
    }

    fun reset() {
        initialized = false
        x = FloatArray(4)
        P = FloatArray(16)
        _trajectory.clear()
    }

    // 4x4 · 4 → 4
    private fun m4v4(m: FloatArray, v: FloatArray) = FloatArray(4) { i ->
        m[i*4]*v[0] + m[i*4+1]*v[1] + m[i*4+2]*v[2] + m[i*4+3]*v[3]
    }

    // 4x4 · 4x4 → 4x4
    private fun m4m4(a: FloatArray, b: FloatArray) = FloatArray(16) { k ->
        val i = k / 4; val j = k % 4
        a[i*4]*b[j] + a[i*4+1]*b[4+j] + a[i*4+2]*b[8+j] + a[i*4+3]*b[12+j]
    }

    // 4x4 + Q·I → 4x4
    private fun m4m4addQ(a: FloatArray) = FloatArray(16) { i ->
        a[i] + if (i % 5 == 0) PROCESS_NOISE_Q else 0f
    }

    // 4x4 · 4x2 → 4x2
    private fun m4x4m4x2(a: FloatArray, b: FloatArray) = FloatArray(8) { k ->
        val i = k / 2; val j = k % 2
        a[i*4]*b[j] + a[i*4+1]*b[2+j] + a[i*4+2]*b[4+j] + a[i*4+3]*b[6+j]
    }

    // 4x2 · 2x2 → 4x2
    private fun m4x2m2x2(a: FloatArray, b: FloatArray) = FloatArray(8) { k ->
        val i = k / 2; val j = k % 2
        a[i*2]*b[j] + a[i*2+1]*b[2+j]
    }

    // 4x2 · 2x4 → 4x4
    private fun m4x2m2x4(a: FloatArray, b: FloatArray) = FloatArray(16) { k ->
        val i = k / 4; val j = k % 4
        a[i*2]*b[j] + a[i*2+1]*b[4+j]
    }
}
```

- [ ] **Step 6: Jalankan KalmanTrackerTest**

```
.\gradlew test --tests "com.example.tennisscorer.tracking.KalmanTrackerTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 7: Jalankan full test suite**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 30 tests pass (24 existing + 6 baru).

- [ ] **Step 8: Commit**

```
git add app/build.gradle.kts app/src/main/java/com/example/tennisscorer/tracking/TrackedBall.kt app/src/main/java/com/example/tennisscorer/tracking/KalmanTracker.kt app/src/test/java/com/example/tennisscorer/tracking/KalmanTrackerTest.kt
git commit -m "feat(tracking): add TrackedBall data class and KalmanTracker with 4-state filter"
```

---

### Task 2: BallTrackingViewModel — Kalman integration

**Files:**
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`

**Interfaces:**
- Consumes:
  - `KalmanTracker()` dan `KalmanTracker.update(detection: Detection?): TrackedBall?` dari Task 1
  - `KalmanTracker.reset()` dari Task 1
  - `TrackedBall` dari Task 1
- Produces:
  - `BallTrackingViewModel.trackedBall: StateFlow<TrackedBall?>`
  - `BallTrackingViewModel.resetTrajectory()`

- [ ] **Step 1: Tambah 2 test baru ke BallTrackingViewModelTest**

Buka `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`. Tambahkan import `com.example.tennisscorer.tracking.TrackedBall` dan 2 test baru di akhir class (sebelum `}`):

```kotlin
package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.TrackedBall
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

    @Test fun `trackedBall starts null`() {
        assertNull(vm.trackedBall.value)
    }

    @Test fun `resetTrajectory sets trackedBall to null`() {
        vm.resetTrajectory()
        assertNull(vm.trackedBall.value)
    }
}
```

- [ ] **Step 2: Jalankan test untuk verifikasi gagal**

```
.\gradlew test --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: `BUILD FAILED` — `Unresolved reference: trackedBall` dan `Unresolved reference: resetTrajectory`

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
import com.example.tennisscorer.tracking.KalmanTracker
import com.example.tennisscorer.tracking.TrackedBall
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

    private val _trackedBall = MutableStateFlow<TrackedBall?>(null)
    val trackedBall: StateFlow<TrackedBall?> = _trackedBall.asStateFlow()

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

    private val kalmanTracker = KalmanTracker()
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
        val detector = BallDetector(context.applicationContext) { detections ->
            _detections.value = detections
            _trackedBall.value = kalmanTracker.update(detections.firstOrNull())
        }
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

    fun resetTrajectory() {
        kalmanTracker.reset()
        _trackedBall.value = null
    }

    override fun onCleared() {
        courtDetector?.close()
        ballDetector?.close()
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
```

- [ ] **Step 4: Jalankan BallTrackingViewModelTest**

```
.\gradlew test --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, 12 tests pass (10 existing + 2 baru).

- [ ] **Step 5: Jalankan full test suite**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 32 tests pass (30 dari Task 1 + 2 baru).

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
git commit -m "feat(tracking): integrate KalmanTracker into BallTrackingViewModel, add trackedBall StateFlow"
```

---

### Task 3: CameraScreen — trail + crosshair overlay

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`

**Interfaces:**
- Consumes:
  - `BallTrackingViewModel.trackedBall: StateFlow<TrackedBall?>` dari Task 2
  - `BallTrackingViewModel.resetTrajectory()` dari Task 2
  - `TrackedBall.position: PointF`, `TrackedBall.velocity: PointF`, `TrackedBall.isPredicted: Boolean`, `TrackedBall.trajectory: List<PointF>` dari Task 1
  - `CyanAccent` dari theme (existing)
  - `Color.Yellow` dari Material3 (existing import)

- [ ] **Step 1: Modify CameraScreen.kt**

Buka `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`.

**Perubahan 1:** Tambah import `com.example.tennisscorer.tracking.TrackedBall` setelah import `CalibrationState`:

```kotlin
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.TrackedBall
```

**Perubahan 2:** Di branch `else` (kamera aktif), tambah `val trackedBall by viewModel.trackedBall.collectAsState()` setelah `calibrationState`:

```kotlin
            else -> {
                val detections by viewModel.detections.collectAsState()
                val calibrationState by viewModel.calibrationState.collectAsState()
                val trackedBall by viewModel.trackedBall.collectAsState()
                val previewView = remember { PreviewView(context) }
```

**Perubahan 3:** Di dalam blok `Canvas(modifier = Modifier.fillMaxSize())`, tambah trail dan crosshair SETELAH loop bounding box bola (`detections.forEach { ... }`):

```kotlin
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
```

Import `TrackedBall` sudah ditambahkan di Perubahan 1. Import `Color` sudah ada (existing). Tidak ada import baru lain yang diperlukan.

- [ ] **Step 2: Verifikasi build**

```
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Deprecation warning `setTargetResolution` adalah pre-existing, acceptable.

- [ ] **Step 3: Jalankan full test suite**

```
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, 32 tests pass (tidak berubah dari Task 2).

- [ ] **Step 4: Manual smoke test**

Install ke device:
```
.\gradlew installDebug
```

Test sequence:
1. SplashScreen → tap "Ball Tracking" → izin kamera → CameraScreen buka, spinner "Mendeteksi lapangan..." muncul
2. **Arahkan ke non-lapangan selama ~2 detik:** teks kuning "Kalibrasi gagal" muncul, ball detection aktif
3. Gerakkan bola di depan kamera → bounding box `CyanAccent` muncul, crosshair `CyanAccent` (lingkaran kecil) muncul di center
4. Sembunyikan bola → crosshair berubah `Color.Yellow`, posisi terus bergerak (prediksi Kalman)
5. Gerakkan bola beberapa frame → trail CyanAccent muncul sebagai garis dari posisi-posisi sebelumnya
6. Navigasi kembali → tidak ada crash

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt
git commit -m "feat(ui): add Kalman trajectory trail and crosshair overlay to CameraScreen"
```
