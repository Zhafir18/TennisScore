# YOLOv8 Upgrade + Court Calibration + Heatmap Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade ball detector ke YOLOv8 fine-tuned tennis ball, perbaiki bug koordinat heatmap + tambah live ball dot di minimap, dan buat kalibrasi lapangan robust di angle rendah dengan fallback 4-tap manual.

**Architecture:** Tiga task independen pada pipeline tracking: (1) ganti EfficientDet dengan YoloV8Detector via raw TFLite Interpreter, (2) denormalisasi posisi bola sebelum HomographyMapper dan tampilkan live dot di minimap, (3) tuning parameter CourtDetector dan tambah alur kalibrasi manual 4-tap ketika auto-detect gagal.

**Tech Stack:** Kotlin, TFLite raw `Interpreter` 2.14.0, OpenCV 4.10.0, Jetpack Compose, CameraX 1.4.1, MockK 1.13.8

## Global Constraints

- Model: `app/src/main/assets/yolov8_tennis_ball.tflite` — harus didownload dari Roboflow sebelum build
- YOLOv8 input: `[1, 640, 640, 3]` float32 NHWC, values 0.0–1.0
- YOLOv8 output: `[1, 5, 8400]` float32 — per anchor: [cx, cy, w, h, conf], coords dalam piksel dari input 640×640
- `CONF_THRESHOLD = 0.35f`, `IOU_THRESHOLD = 0.45f`
- `IMAGE_WIDTH = 640f`, `IMAGE_HEIGHT = 480f` (sesuai ImageAnalysis config di CameraScreen)
- CourtDetector: `HOUGH_MIN_LINE_LENGTH = 50.0`, `MIN_QUAD_AREA_RATIO = 0.05`, `ANGLE_TOLERANCE_DEG = 30.0`
- Urutan 4-tap: nearLeft → nearRight → farLeft → farRight
- Semua test yang ada harus tetap PASS setelah setiap commit

---

### Task 1: YoloV8Detector — replace BallDetector dengan raw TFLite Interpreter

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/tracking/YoloV8Detector.kt`
- Create: `app/src/test/java/com/example/tennisscorer/tracking/YoloV8DetectorTest.kt`
- Delete: `app/src/main/java/com/example/tennisscorer/tracking/BallDetector.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`

**Interfaces:**
- Produces: `YoloV8Detector(context, onDetections: (List<Detection>) -> Unit) : FrameAnalyzer`
  - `fun close()`
  - `companion object.parseOutput(output: Array<FloatArray>, confThreshold, iouThreshold): List<Detection>`
  - `companion object.nms(candidates: List<Pair<RectF, Float>>, iouThreshold): List<Pair<RectF, Float>>`
  - `companion object.iou(a: RectF, b: RectF): Float`

- [ ] **Step 1: Download model file (manual — lakukan dulu sebelum step lainnya)**

  1. Buka https://universe.roboflow.com/yolov8-xo2x7/tennisballtracker-mp7wb
  2. Login → tab **Model** → **Export** → format **TFLite** → Download
  3. Rename file menjadi `yolov8_tennis_ball.tflite`
  4. Salin ke: `app/src/main/assets/yolov8_tennis_ball.tflite`

- [ ] **Step 2: Update libs.versions.toml — ganti tflite dependency**

  Di `gradle/libs.versions.toml`:

  **Hapus** dari `[versions]`:
  ```toml
  tflite-task-vision = "0.4.4"
  ```
  **Tambahkan** di `[versions]`:
  ```toml
  tflite = "2.14.0"
  ```

  **Hapus** dari `[libraries]`:
  ```toml
  tflite-task-vision = { group = "org.tensorflow", name = "tensorflow-lite-task-vision", version.ref = "tflite-task-vision" }
  ```
  **Tambahkan** di `[libraries]`:
  ```toml
  tflite = { group = "org.tensorflow", name = "tensorflow-lite", version.ref = "tflite" }
  ```

- [ ] **Step 3: Update app/build.gradle.kts**

  **Hapus**:
  ```kotlin
  implementation(libs.tflite.task.vision)
  ```
  **Tambahkan**:
  ```kotlin
  implementation(libs.tflite)
  ```

- [ ] **Step 4: Tulis failing tests**

  Buat `app/src/test/java/com/example/tennisscorer/tracking/YoloV8DetectorTest.kt`:

  ```kotlin
  package com.example.tennisscorer.tracking

  import android.graphics.RectF
  import org.junit.Assert.*
  import org.junit.Test

  class YoloV8DetectorTest {

      private fun makeOutput(anchors: List<FloatArray>): Array<FloatArray> {
          // anchors: list of [cx, cy, w, h, conf] in 640-pixel space
          val out = Array(5) { FloatArray(8400) }
          anchors.forEachIndexed { i, v ->
              out[0][i] = v[0]; out[1][i] = v[1]
              out[2][i] = v[2]; out[3][i] = v[3]
              out[4][i] = v[4]
          }
          return out
      }

      @Test fun `parseOutput empty when all confidence below threshold`() {
          val output = makeOutput(listOf(floatArrayOf(320f, 240f, 50f, 50f, 0.1f)))
          assertTrue(YoloV8Detector.parseOutput(output).isEmpty())
      }

      @Test fun `parseOutput returns detection above threshold`() {
          val output = makeOutput(listOf(floatArrayOf(320f, 240f, 64f, 64f, 0.9f)))
          val result = YoloV8Detector.parseOutput(output)
          assertEquals(1, result.size)
          assertEquals(0.9f, result[0].confidence, 0.001f)
      }

      @Test fun `parseOutput normalizes coords to 0-1`() {
          val output = makeOutput(listOf(floatArrayOf(320f, 240f, 64f, 64f, 0.8f)))
          val result = YoloV8Detector.parseOutput(output)
          val box = result[0].boundingBox
          // cx=320, cy=240, w=64, h=64 → left=(320-32)/640, top=(240-32)/640, ...
          assertEquals((320f - 32f) / 640f, box.left,   0.001f)
          assertEquals((240f - 32f) / 640f, box.top,    0.001f)
          assertEquals((320f + 32f) / 640f, box.right,  0.001f)
          assertEquals((240f + 32f) / 640f, box.bottom, 0.001f)
      }

      @Test fun `parseOutput clamps coords to 0-1`() {
          val output = makeOutput(listOf(floatArrayOf(0f, 240f, 100f, 64f, 0.8f)))
          val result = YoloV8Detector.parseOutput(output)
          assertEquals(1, result.size)
          assertTrue(result[0].boundingBox.left >= 0f)
      }

      @Test fun `nms suppresses overlapping boxes`() {
          val box1 = RectF(0f, 0f, 100f, 100f)
          val box2 = RectF(10f, 10f, 110f, 110f)
          val candidates = listOf(Pair(box1, 0.9f), Pair(box2, 0.8f))
          val result = YoloV8Detector.nms(candidates)
          assertEquals(1, result.size)
          assertEquals(0.9f, result[0].second, 0.001f)
      }

      @Test fun `nms keeps non-overlapping boxes`() {
          val box1 = RectF(0f, 0f, 50f, 50f)
          val box2 = RectF(200f, 200f, 250f, 250f)
          val candidates = listOf(Pair(box1, 0.9f), Pair(box2, 0.8f))
          assertEquals(2, YoloV8Detector.nms(candidates).size)
      }

      @Test fun `iou zero for non-overlapping boxes`() {
          val a = RectF(0f, 0f, 10f, 10f)
          val b = RectF(20f, 20f, 30f, 30f)
          assertEquals(0f, YoloV8Detector.iou(a, b), 0.001f)
      }

      @Test fun `iou one for identical boxes`() {
          val a = RectF(0f, 0f, 10f, 10f)
          assertEquals(1f, YoloV8Detector.iou(a, a), 0.001f)
      }

      @Test fun `iou partial overlap correct`() {
          val a = RectF(0f, 0f, 10f, 10f)
          val b = RectF(5f, 0f, 15f, 10f)
          // intersection=50, union=150
          assertEquals(50f / 150f, YoloV8Detector.iou(a, b), 0.001f)
      }
  }
  ```

- [ ] **Step 5: Jalankan tests untuk verifikasi FAIL**

  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.tennisscorer.tracking.YoloV8DetectorTest"
  ```
  Expected: FAIL — `YoloV8Detector` not found.

- [ ] **Step 6: Buat YoloV8Detector.kt**

  Buat `app/src/main/java/com/example/tennisscorer/tracking/YoloV8Detector.kt`:

  ```kotlin
  package com.example.tennisscorer.tracking

  import android.content.Context
  import android.graphics.Bitmap
  import android.graphics.RectF
  import androidx.camera.core.ImageProxy
  import org.tensorflow.lite.Interpreter
  import java.io.FileInputStream
  import java.nio.ByteBuffer
  import java.nio.ByteOrder
  import java.nio.channels.FileChannel

  class YoloV8Detector(
      context: Context,
      private val onDetections: (List<Detection>) -> Unit
  ) : FrameAnalyzer {

      private val interpreter: Interpreter = run {
          val fd = context.assets.openFd(MODEL_FILE)
          val mapped = FileInputStream(fd.fileDescriptor)
              .channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
          Interpreter(mapped)
      }

      override fun analyze(image: ImageProxy) {
          val bitmap = image.toBitmap()
          image.close()
          val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
          val inputBuffer = bitmapToByteBuffer(scaled)
          val output = Array(1) { Array(5) { FloatArray(NUM_ANCHORS) } }
          interpreter.run(inputBuffer, output)
          onDetections(parseOutput(output[0]))
      }

      private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
          val buf = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
          buf.order(ByteOrder.nativeOrder())
          val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
          bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
          for (pixel in pixels) {
              buf.putFloat(((pixel shr 16) and 0xFF) / 255f)
              buf.putFloat(((pixel shr 8)  and 0xFF) / 255f)
              buf.putFloat((pixel          and 0xFF) / 255f)
          }
          return buf
      }

      fun close() { interpreter.close() }

      companion object {
          const val MODEL_FILE     = "yolov8_tennis_ball.tflite"
          const val CONF_THRESHOLD = 0.35f
          const val IOU_THRESHOLD  = 0.45f
          const val INPUT_SIZE     = 640
          const val NUM_ANCHORS    = 8400

          fun parseOutput(
              output: Array<FloatArray>,
              confThreshold: Float = CONF_THRESHOLD,
              iouThreshold: Float  = IOU_THRESHOLD
          ): List<Detection> {
              val candidates = mutableListOf<Pair<RectF, Float>>()
              for (i in 0 until NUM_ANCHORS) {
                  val conf = output[4][i]
                  if (conf < confThreshold) continue
                  val cx = output[0][i]; val cy = output[1][i]
                  val w  = output[2][i]; val h  = output[3][i]
                  candidates.add(Pair(RectF(cx - w/2f, cy - h/2f, cx + w/2f, cy + h/2f), conf))
              }
              return nms(candidates, iouThreshold).map { (box, conf) ->
                  Detection(
                      boundingBox = RectF(
                          (box.left   / INPUT_SIZE).coerceIn(0f, 1f),
                          (box.top    / INPUT_SIZE).coerceIn(0f, 1f),
                          (box.right  / INPUT_SIZE).coerceIn(0f, 1f),
                          (box.bottom / INPUT_SIZE).coerceIn(0f, 1f)
                      ),
                      confidence = conf
                  )
              }
          }

          fun nms(
              candidates: List<Pair<RectF, Float>>,
              iouThreshold: Float = IOU_THRESHOLD
          ): List<Pair<RectF, Float>> {
              val sorted = candidates.sortedByDescending { it.second }
              val suppressed = BooleanArray(sorted.size)
              val kept = mutableListOf<Pair<RectF, Float>>()
              for (i in sorted.indices) {
                  if (suppressed[i]) continue
                  kept.add(sorted[i])
                  for (j in i + 1 until sorted.size) {
                      if (!suppressed[j] && iou(sorted[i].first, sorted[j].first) > iouThreshold)
                          suppressed[j] = true
                  }
              }
              return kept
          }

          fun iou(a: RectF, b: RectF): Float {
              val ix = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left))
              val iy = maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
              val inter = ix * iy
              if (inter == 0f) return 0f
              return inter / (a.width() * a.height() + b.width() * b.height() - inter)
          }
      }
  }
  ```

- [ ] **Step 7: Jalankan tests untuk verifikasi PASS**

  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.tennisscorer.tracking.YoloV8DetectorTest"
  ```
  Expected: 8 tests PASS.

- [ ] **Step 8: Update BallTrackingViewModel — ganti BallDetector → YoloV8Detector**

  Di `BallTrackingViewModel.kt`:

  **Hapus** import:
  ```kotlin
  import com.example.tennisscorer.tracking.BallDetector
  ```
  **Tambahkan** import:
  ```kotlin
  import com.example.tennisscorer.tracking.YoloV8Detector
  ```

  **Ubah** deklarasi field (baris `private var ballDetector`):
  ```kotlin
  private var ballDetector: YoloV8Detector? = null
  ```

  **Ubah** seluruh fungsi `initDetector`:
  ```kotlin
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
  ```

  `onCleared()` tidak perlu diubah — `ballDetector?.close()` masih valid.

- [ ] **Step 9: Hapus BallDetector.kt**

  ```
  git rm app/src/main/java/com/example/tennisscorer/tracking/BallDetector.kt
  ```

- [ ] **Step 10: Jalankan semua tests**

  ```
  .\gradlew.bat :app:testDebugUnitTest
  ```
  Expected: semua test PASS.

- [ ] **Step 11: Commit**

  ```
  git add gradle/libs.versions.toml app/build.gradle.kts
  git add app/src/main/java/com/example/tennisscorer/tracking/YoloV8Detector.kt
  git add app/src/test/java/com/example/tennisscorer/tracking/YoloV8DetectorTest.kt
  git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt
  git commit -m "feat(tracking): replace BallDetector with YoloV8Detector using raw TFLite Interpreter"
  ```

---

### Task 2: Heatmap Coordinate Fix + Live Ball Dot di Minimap

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/components/CourtHeatmapView.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`

**Interfaces:**
- Consumes: `YoloV8Detector` (dari Task 1), `HomographyMapper.mapToCourtCoords(PointF)`
- Produces:
  - `BallTrackingViewModel.ballCourtPos: StateFlow<PointF?>` — posisi bola dalam meter court space
  - `BallTrackingViewModel.IMAGE_WIDTH = 640f`, `IMAGE_HEIGHT = 480f`
  - `CourtHeatmapView(heatmapBitmap, bounceCount, liveBallCourtPos: PointF? = null, modifier)`

- [ ] **Step 1: Tulis failing tests untuk ballCourtPos**

  Tambahkan di akhir class `BallTrackingViewModelTest` (sebelum `}`):

  ```kotlin
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
  ```

- [ ] **Step 2: Jalankan tests untuk verifikasi FAIL**

  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
  ```
  Expected: FAIL — `ballCourtPos` unresolved reference.

- [ ] **Step 3: Update BallTrackingViewModel — tambah IMAGE_WIDTH/HEIGHT, ballCourtPos, fix processBallUpdate**

  **1. Tambahkan** `IMAGE_WIDTH` dan `IMAGE_HEIGHT` ke companion object yang sudah ada:
  ```kotlin
  companion object {
      fun factory(engine: TennisScoreEngine, bounceRepo: BounceRepository): ViewModelProvider.Factory = viewModelFactory {
          initializer { BallTrackingViewModel(engine, bounceRepo) }
      }
      const val IMAGE_WIDTH  = 640f
      const val IMAGE_HEIGHT = 480f
  }
  ```

  **2. Tambahkan** StateFlow baru setelah `_trackedBall`/`trackedBall`:
  ```kotlin
  private val _ballCourtPos = MutableStateFlow<PointF?>(null)
  val ballCourtPos: StateFlow<PointF?> = _ballCourtPos.asStateFlow()
  ```

  **3. Ganti seluruh fungsi `processBallUpdate`**:
  ```kotlin
  internal fun processBallUpdate(detection: Detection?) {
      val tracked = kalmanTracker.update(detection)
      _trackedBall.value = tracked
      if (tracked != null) {
          val mapper = (_calibrationState.value as? CalibrationState.Calibrated)?.mapper
          val courtPos = mapper?.mapToCourtCoords(
              PointF(tracked.position.x * IMAGE_WIDTH, tracked.position.y * IMAGE_HEIGHT)
          )
          _ballCourtPos.value = courtPos
          val event = bounceDetector.process(tracked.velocity.y, courtPos, tracked.isPredicted)
          if (event is BounceEvent.PointAwarded) handleBounceEvent(event)
      } else {
          _ballCourtPos.value = null
      }
  }
  ```
  (Baris lama `val courtPos = mapper?.mapToCourtCoords(tracked.position)` dihapus dan diganti dengan blok di atas.)

- [ ] **Step 4: Jalankan tests**

  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
  ```
  Expected: semua test PASS (termasuk 3 test baru).

- [ ] **Step 5: Update CourtHeatmapView — tambah liveBallCourtPos + dot**

  Di `CourtHeatmapView.kt`:

  **Tambahkan** import:
  ```kotlin
  import android.graphics.PointF
  ```

  **Ubah** signature composable:
  ```kotlin
  @Composable
  fun CourtHeatmapView(
      heatmapBitmap: Bitmap?,
      bounceCount: Int,
      liveBallCourtPos: PointF? = null,
      modifier: Modifier = Modifier
  )
  ```

  Di dalam `Canvas(modifier = Modifier.fillMaxSize())`, **tambahkan** blok berikut setelah blok `heatmapBitmap?.let { bmp -> ... }`:
  ```kotlin
  liveBallCourtPos?.let { pos ->
      val bx = (pos.x / HomographyMapper.COURT_WIDTH_M) * size.width
      val by = (pos.y / HomographyMapper.COURT_LENGTH_M) * size.height
      if (bx in 0f..size.width && by in 0f..size.height) {
          drawCircle(
              color = Color(0xFF00FFFF),
              radius = 4.dp.toPx(),
              center = Offset(bx, by)
          )
      }
  }
  ```

- [ ] **Step 6: Update CameraScreen — collect ballCourtPos, pass ke CourtHeatmapView**

  Di `CameraScreen.kt`, di blok `else ->` setelah baris:
  ```kotlin
  val bounceCount by viewModel.bounceCount.collectAsState()
  ```
  tambahkan:
  ```kotlin
  val ballCourtPos by viewModel.ballCourtPos.collectAsState()
  ```

  Cari pemanggilan `CourtHeatmapView(...)` dan ubah menjadi:
  ```kotlin
  CourtHeatmapView(
      heatmapBitmap = heatmapBitmap,
      bounceCount = bounceCount,
      liveBallCourtPos = ballCourtPos,
      modifier = Modifier.fillMaxSize()
  )
  ```

- [ ] **Step 7: Jalankan semua tests**

  ```
  .\gradlew.bat :app:testDebugUnitTest
  ```
  Expected: semua test PASS.

- [ ] **Step 8: Commit**

  ```
  git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt
  git add app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
  git add app/src/main/java/com/example/tennisscorer/ui/components/CourtHeatmapView.kt
  git add app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt
  git commit -m "fix(heatmap): denormalize coords before HomographyMapper, add live ball dot in minimap"
  ```

---

### Task 3: Court Calibration Robustness — Hough tuning + Manual 4-tap fallback

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/tracking/CalibrationState.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/tracking/CourtDetector.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`

**Interfaces:**
- Consumes: `CalibrationState` (sealed), `IMAGE_WIDTH`/`IMAGE_HEIGHT` (dari Task 2), `HomographyMapper`
- Produces:
  - `CalibrationState.ManualCalibrating(taps: List<PointF>)`
  - `BallTrackingViewModel.startManualCalibration()`
  - `BallTrackingViewModel.addManualTap(tap: PointF)`
  - `BallTrackingViewModel.retryAutoCalibration(context: Context)`

- [ ] **Step 1: Tulis failing tests untuk manual calibration state machine**

  Tambahkan di akhir class `BallTrackingViewModelTest` (sebelum `}`):

  ```kotlin
  @Test fun `startManualCalibration transitions to ManualCalibrating with empty taps`() {
      vm.startManualCalibration()
      val state = vm.calibrationState.value
      assertTrue(state is CalibrationState.ManualCalibrating)
      assertEquals(0, (state as CalibrationState.ManualCalibrating).taps.size)
  }

  @Test fun `addManualTap accumulates taps in ManualCalibrating state`() {
      vm.startManualCalibration()
      val tap1 = android.graphics.PointF(0.1f, 0.9f)
      val tap2 = android.graphics.PointF(0.9f, 0.9f)
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
  ```

- [ ] **Step 2: Jalankan tests untuk verifikasi FAIL**

  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
  ```
  Expected: FAIL — `CalibrationState.ManualCalibrating`, `startManualCalibration`, `addManualTap` not found.

- [ ] **Step 3: Update CalibrationState.kt — tambah ManualCalibrating**

  Ganti seluruh isi `CalibrationState.kt`:
  ```kotlin
  package com.example.tennisscorer.tracking

  import android.graphics.PointF

  sealed class CalibrationState {
      object Uncalibrated : CalibrationState()
      object Calibrating : CalibrationState()
      data class ManualCalibrating(val taps: List<PointF> = emptyList()) : CalibrationState()
      data class Calibrated(val mapper: HomographyMapper) : CalibrationState()
      data class Failed(val reason: String) : CalibrationState()
  }
  ```

- [ ] **Step 4: Update CourtDetector.kt — tuning parameter + white-line enhancement**

  **Ubah** companion object constants:
  ```kotlin
  companion object {
      const val MAX_FRAMES = 60
      private const val CANNY_LOW = 50.0
      private const val CANNY_HIGH = 150.0
      private const val HOUGH_THRESHOLD = 80
      private const val HOUGH_MIN_LINE_LENGTH = 50.0    // was 100.0
      private const val HOUGH_MAX_LINE_GAP = 10.0
      private const val ANGLE_TOLERANCE_DEG = 30.0      // was 20.0
      private const val MIN_QUAD_AREA_RATIO = 0.05      // was 0.15
      private const val WHITE_THRESHOLD = 180.0
  }
  ```

  Di `tryDetectCourt`, setelah baris:
  ```kotlin
  Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
  ```
  **Tambahkan** satu baris:
  ```kotlin
  Imgproc.threshold(grayMat, grayMat, WHITE_THRESHOLD, 255.0, Imgproc.THRESH_TOZERO)
  ```
  (Baris `Imgproc.Canny(...)` yang sudah ada tetap di bawahnya — tidak diubah.)

- [ ] **Step 5: Update BallTrackingViewModel — tambah savedAppContext, manual calibration methods, retryAutoCalibration**

  **Tambahkan** imports berikut (jika belum ada):
  ```kotlin
  import org.opencv.core.Mat
  import org.opencv.core.MatOfPoint2f
  import org.opencv.core.Point
  import org.opencv.imgproc.Imgproc
  ```

  **Tambahkan** field setelah `private var courtDetector: CourtDetector? = null`:
  ```kotlin
  private var savedAppContext: Context? = null
  ```

  **Di `initCalibration`**, tambahkan baris pertama setelah guard (setelah `if (_calibrationState.value != CalibrationState.Uncalibrated) return`):
  ```kotlin
  savedAppContext = context.applicationContext
  ```

  **Tambahkan** fungsi-fungsi baru setelah `initCalibration`:
  ```kotlin
  fun startManualCalibration() {
      _calibrationState.value = CalibrationState.ManualCalibrating()
  }

  fun addManualTap(tap: PointF) {
      val current = _calibrationState.value as? CalibrationState.ManualCalibrating ?: return
      val newTaps = current.taps + tap
      if (newTaps.size < 4) {
          _calibrationState.value = CalibrationState.ManualCalibrating(newTaps)
      } else {
          applyManualCalibration(newTaps)
      }
  }

  private fun applyManualCalibration(taps: List<PointF>) {
      // taps[0]=nearLeft, [1]=nearRight, [2]=farLeft, [3]=farRight (normalized 0-1)
      var src: MatOfPoint2f? = null; var dst: MatOfPoint2f? = null; var H: Mat? = null
      val matrix = try {
          src = MatOfPoint2f(
              Point(taps[0].x * IMAGE_WIDTH.toDouble(), taps[0].y * IMAGE_HEIGHT.toDouble()),
              Point(taps[1].x * IMAGE_WIDTH.toDouble(), taps[1].y * IMAGE_HEIGHT.toDouble()),
              Point(taps[2].x * IMAGE_WIDTH.toDouble(), taps[2].y * IMAGE_HEIGHT.toDouble()),
              Point(taps[3].x * IMAGE_WIDTH.toDouble(), taps[3].y * IMAGE_HEIGHT.toDouble())
          )
          dst = MatOfPoint2f(
              Point(0.0, 0.0),
              Point(HomographyMapper.COURT_WIDTH_M.toDouble(), 0.0),
              Point(0.0, HomographyMapper.COURT_LENGTH_M.toDouble()),
              Point(HomographyMapper.COURT_WIDTH_M.toDouble(), HomographyMapper.COURT_LENGTH_M.toDouble())
          )
          H = Imgproc.getPerspectiveTransform(src, dst)
          FloatArray(9) { i -> H.get(i / 3, i % 3)[0].toFloat() }
      } catch (_: Throwable) {
          null
      } finally {
          src?.release(); dst?.release(); H?.release()
      }
      if (matrix != null) {
          _calibrationState.value = CalibrationState.Calibrated(HomographyMapper(matrix))
          savedAppContext?.let { initDetector(it) }
      } else {
          _calibrationState.value = CalibrationState.Failed("Kalibrasi manual gagal")
      }
  }

  fun retryAutoCalibration(context: Context) {
      setFrameAnalyzer(null)
      courtDetector?.close()
      courtDetector = null
      ballDetector?.close()   // reset agar initDetector bisa re-create dan re-set analyzer
      ballDetector = null
      _calibrationState.value = CalibrationState.Uncalibrated
      initCalibration(context)
  }
  ```

- [ ] **Step 6: Jalankan tests**

  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
  ```
  Expected: semua test PASS (termasuk 4 test baru ManualCalibrating).

- [ ] **Step 7: Update CameraScreen — ubah Failed UI + tambah ManualCalibrating overlay**

  **Tambahkan** imports berikut di `CameraScreen.kt` (jika belum ada):
  ```kotlin
  import android.graphics.PointF
  import androidx.compose.foundation.gestures.detectTapGestures
  import androidx.compose.foundation.layout.BoxWithConstraints
  import androidx.compose.ui.input.pointer.pointerInput
  ```

  Di dalam `when (calibrationState)`:

  **Ganti** seluruh blok `is CalibrationState.Failed -> { Text(...) }` dengan:
  ```kotlin
  is CalibrationState.Failed -> {
      Column(
          modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 8.dp, start = 16.dp, end = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
      ) {
          Text(
              text = "Kalibrasi gagal — koordinat lapangan tidak tersedia",
              color = Color.Yellow,
              fontSize = 11.sp,
              textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                  onClick = { viewModel.retryAutoCalibration(context) },
                  colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
              ) { Text("Coba Ulang Auto", color = Color.White, fontSize = 11.sp) }
              Button(
                  onClick = { viewModel.startManualCalibration() },
                  colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
              ) { Text("Kalibrasi Manual", color = Color.White, fontSize = 11.sp) }
          }
      }
  }
  ```

  **Tambahkan** case baru setelah `is CalibrationState.Failed -> { ... }`:
  ```kotlin
  is CalibrationState.ManualCalibrating -> {
      val taps = (calibrationState as CalibrationState.ManualCalibrating).taps
      val tapLabels = listOf("Dekat-Kiri", "Dekat-Kanan", "Jauh-Kiri", "Jauh-Kanan")
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val maxW = constraints.maxWidth.toFloat()
          val maxH = constraints.maxHeight.toFloat()
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .background(Color.Black.copy(alpha = 0.55f))
                  .pointerInput(Unit) {
                      detectTapGestures { offset ->
                          viewModel.addManualTap(PointF(offset.x / maxW, offset.y / maxH))
                      }
                  }
          ) {
              Column(
                  modifier = Modifier
                      .align(Alignment.TopCenter)
                      .padding(top = 24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
              ) {
                  Text(
                      text = if (taps.size < 4) "Sentuh sudut ${tapLabels[taps.size]}"
                             else "Memproses...",
                      color = Color.White,
                      fontSize = 16.sp
                  )
                  Spacer(Modifier.height(4.dp))
                  Text(
                      text = "${taps.size}/4 sudut dipilih",
                      color = Color.White.copy(alpha = 0.7f),
                      fontSize = 12.sp
                  )
              }
              Canvas(modifier = Modifier.fillMaxSize()) {
                  taps.forEachIndexed { idx, tap ->
                      drawCircle(
                          color = CyanAccent,
                          radius = 8.dp.toPx(),
                          center = Offset(tap.x * size.width, tap.y * size.height)
                      )
                      drawContext.canvas.nativeCanvas.drawText(
                          "${idx + 1}",
                          tap.x * size.width + 12.dp.toPx(),
                          tap.y * size.height + 4.dp.toPx(),
                          android.graphics.Paint().apply {
                              color = android.graphics.Color.WHITE
                              textSize = 14.sp.toPx()
                          }
                      )
                  }
              }
              TextButton(
                  onClick = { viewModel.retryAutoCalibration(context) },
                  modifier = Modifier
                      .align(Alignment.BottomCenter)
                      .padding(bottom = 80.dp)
              ) {
                  Text("Batal — Coba Ulang Auto", color = Color.White.copy(alpha = 0.7f))
              }
          }
      }
  }
  ```

- [ ] **Step 8: Jalankan semua tests**

  ```
  .\gradlew.bat :app:testDebugUnitTest
  ```
  Expected: semua test PASS.

- [ ] **Step 9: Commit**

  ```
  git add app/src/main/java/com/example/tennisscorer/tracking/CalibrationState.kt
  git add app/src/main/java/com/example/tennisscorer/tracking/CourtDetector.kt
  git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt
  git add app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
  git add app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt
  git commit -m "feat(calibration): tune CourtDetector params for low angle + add manual 4-tap fallback"
  ```
