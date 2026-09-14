# Design: YOLOv8 Upgrade + Court Calibration + Heatmap Fixes

**Date:** 2026-09-14  
**Status:** Approved

---

## Overview

Tiga perbaikan pada pipeline ball tracking TennisScorer:

1. **YOLOv8 Model Upgrade** — ganti `BallDetector` (EfficientDet Lite0 COCO, Task Vision API) dengan `YoloV8Detector` (model fine-tuned tennis ball, raw TFLite `Interpreter`)
2. **Court Calibration Robustness** — longgarkan parameter Hough untuk perspektif rendah + tambah manual 4-tap fallback
3. **Heatmap Fixes** — perbaiki bug koordinat `HomographyMapper` + tambah live ball dot di minimap

---

## 1. YOLOv8 Model Upgrade

### Motivasi

EfficientDet Lite0 COCO memiliki class `"sports ball"` yang dilatih pada berbagai bola besar (sepak bola, basket, dll.) dan tidak cukup akurat untuk bola tenis kecil dari jarak > 5m. YOLOv8 fine-tuned dengan dataset bola tenis akan jauh lebih akurat.

### Cara Mendapatkan Model

1. Buka [Roboflow Universe – TennisBallTracker](https://universe.roboflow.com/yolov8-xo2x7/tennisballtracker-mp7wb)
2. Login/daftar akun Roboflow (gratis)
3. Pilih tab **"Model"** → **"Export"** → format **"TFLite"**
4. Download file `.tflite` (biasanya bernama `best.tflite` atau `model.tflite`)
5. Rename jadi `yolov8_tennis_ball.tflite`
6. Letakkan di `app/src/main/assets/yolov8_tennis_ball.tflite`

### Format Output YOLOv8 TFLite

Model single-class (1 class: tennis ball):
- **Input:** `[1, 640, 640, 3]` — float32, NHWC, nilai 0.0–1.0
- **Output:** `[1, 5, 8400]` — float32
  - Dimensi 1: 5 features per anchor = [cx, cy, w, h, confidence]
  - cx, cy, w, h dalam piksel relatif terhadap input 640×640
  - 8400 anchor dari 3 skala: 80×80 + 40×40 + 20×20

### Arsitektur `YoloV8Detector`

```
YoloV8Detector : FrameAnalyzer
  ├── init(context): load Interpreter dari assets
  ├── analyze(ImageProxy):
  │     1. toBitmap() → close image
  │     2. scaleBitmapTo640x640()
  │     3. bitmapToByteBuffer() — normalize /255f
  │     4. interpreter.run(inputBuffer, outputArray)
  │     5. parseOutput() — filter confidence > CONF_THRESHOLD
  │     6. nms() — non-maximum suppression
  │     7. mapToNormalized() — bagi dengan 640f → coords 0-1
  │     8. onDetections(List<Detection>)
  └── close(): interpreter.close()
```

**Constants:**
```kotlin
MODEL_FILE = "yolov8_tennis_ball.tflite"
CONF_THRESHOLD = 0.35f   // lebih rendah dari sebelumnya (0.4f) untuk bola jauh
IOU_THRESHOLD  = 0.45f   // NMS IoU threshold
INPUT_SIZE     = 640
```

**NMS (manual):**
- Sort deteksi descending by confidence
- Untuk setiap deteksi, hitung IoU dengan semua deteksi yang sudah dipilih
- Hapus jika IoU > `IOU_THRESHOLD`

### Perubahan di ViewModel

- `initDetector()` membuat `YoloV8Detector` (gantikan `BallDetector`)
- `BallDetector.kt` dihapus, `BallDetectorTest` diupdate/dihapus
- Dependency `tensorflow-lite-task-vision` **dihapus** dari `build.gradle`
- Dependency `org.tensorflow:tensorflow-lite:2.14.0` **ditambahkan** (raw Interpreter)

### Dependencies

```kotlin
// build.gradle (:app)
// HAPUS:
// implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

// TAMBAHKAN:
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
// tensorflow-lite-support dipakai untuk TensorImage resize helper
```

---

## 2. Court Calibration Robustness

### Root Cause

Parameter Hough fixed tidak cocok untuk perspektif rendah (kamera tripod normal ~1.2m):
- Garis baseline sisi jauh sangat pendek karena foreshortening → tidak lolos `HOUGH_MIN_LINE_LENGTH = 100`
- Proyeksi court kecil → tidak lolos `MIN_QUAD_AREA_RATIO = 0.15`
- Garis tampak miring lebih dari 20° → tidak dikategorikan sebagai horizontal

### 2a. Tuning Parameter CourtDetector

| Konstanta | Nilai Lama | Nilai Baru | Alasan |
|-----------|-----------|-----------|--------|
| `HOUGH_MIN_LINE_LENGTH` | 100.0 | 50.0 | Garis jauh lebih pendek saat kamera rendah |
| `MIN_QUAD_AREA_RATIO` | 0.15 | 0.05 | Proyeksi lapangan lebih kecil |
| `ANGLE_TOLERANCE_DEG` | 20.0 | 30.0 | Perspektif kuat → garis lebih miring |

**Tambah white-line enhancement sebelum Canny:**
```kotlin
// Sebelum Canny: tingkatkan kontras garis putih
Imgproc.threshold(grayMat, grayMat, 180.0, 255.0, Imgproc.THRESH_TOZERO)
```
Ini mengabaikan pixel abu-abu/gelap dan hanya menyisakan garis putih court, mengurangi noise Hough dari objek lain.

### 2b. Manual 4-Tap Calibration Fallback

#### State Baru di CalibrationState

```kotlin
sealed class CalibrationState {
    object Uncalibrated : CalibrationState()
    object Calibrating : CalibrationState()
    data class ManualCalibrating(val taps: List<Offset> = emptyList()) : CalibrationState()
    data class Calibrated(val mapper: HomographyMapper) : CalibrationState()
    data class Failed(val reason: String) : CalibrationState()
}
```

#### UI Overlay (CameraScreen)

Tampil ketika `calibrationState is ManualCalibrating`:

```
┌─────────────────────────────────────┐
│   Sentuh 4 sudut lapangan           │
│   ① Dekat-Kiri   ② Dekat-Kanan     │
│   ③ Jauh-Kiri    ④ Jauh-Kanan      │
│                                     │
│   [●] Titik yang sudah disentuh     │
│   [○] Titik berikutnya (berkedip)   │
└─────────────────────────────────────┘
```

- Canvas fullscreen dengan `pointerInput(Unit) { detectTapGestures { ... } }`
- Setiap tap: `Offset` dari `detectTapGestures` dalam screen pixels → normalisasi dengan `BoxWithConstraints` maxWidth/maxHeight → simpan sebagai 0–1
- `applyManualCalibration` mengalikan dengan 640×480 untuk mendapat image pixel coords (asumsi: preview mengisi layar tanpa crop berbeda dengan analysis image; cukup untuk akurasi praktis)
- Setelah tap ke-4 → panggil `viewModel.applyManualCalibration(taps)`
- Tampil titik cyan kecil di setiap tap yang sudah dilakukan
- Teks instruktif berubah sesuai urutan: "Sentuh sudut Dekat-Kiri" → "Sentuh sudut Dekat-Kanan" → dst.

#### Urutan Tap yang Diterima

Urutan sudut yang disentuh: **Dekat-Kiri → Dekat-Kanan → Jauh-Kiri → Jauh-Kanan**
(Sama persis dengan urutan `bl, br, tl, tr` di `tryDetectCourt`)

#### Perubahan di ViewModel

```kotlin
fun startManualCalibration() {
    _calibrationState.value = CalibrationState.ManualCalibrating()
}

fun addManualTap(tap: Offset) {
    val current = _calibrationState.value as? CalibrationState.ManualCalibrating ?: return
    val taps = current.taps + tap
    if (taps.size < 4) {
        _calibrationState.value = CalibrationState.ManualCalibrating(taps)
    } else {
        applyManualCalibration(taps)
    }
}

private fun applyManualCalibration(taps: List<Offset>) {
    // taps[0]=nearLeft, [1]=nearRight, [2]=farLeft, [3]=farRight (normalized 0-1)
    // Konversi ke piksel image (640×480)
    val src = taps.map { Point(it.x * IMAGE_WIDTH.toDouble(), it.y * IMAGE_HEIGHT.toDouble()) }
    val matrix = computeHomographyFromPoints(src)  // panggil OpenCV Imgproc.getPerspectiveTransform
    if (matrix != null) {
        _calibrationState.value = CalibrationState.Calibrated(HomographyMapper(matrix))
        savedAppContext?.let { initDetector(it) }
    } else {
        _calibrationState.value = CalibrationState.Failed("Kalibrasi manual gagal")
    }
}

// savedAppContext disimpan saat initCalibration dipanggil (applicationContext — aman)
private var savedAppContext: Context? = null
```

`computeHomographyFromPoints` adalah fungsi pure yang memanggil `Imgproc.getPerspectiveTransform` sama seperti di `CourtDetector`, dapat di-test.

#### Tombol "Kalibrasi Manual" di CameraScreen

Ketika `calibrationState is Failed`:
```
┌──────────────────────────────────┐
│ ⚠ Kalibrasi gagal               │
│ [Coba Ulang Auto] [Kalibrasi Manual] │
└──────────────────────────────────┘
```

---

## 3. Heatmap Bug Fixes

### 3a. Bug Koordinat HomographyMapper

**Root cause:** `HomographyMapper.mapToCourtCoords(normalizedPoint)` menerima koordinat 0–1 (normalized screen), tapi homography matrix dikompute dengan koordinat piksel (0–640, 0–480). Hasil map mendekati 0 untuk semua titik.

**Fix di `processBallUpdate`:**
```kotlin
internal fun processBallUpdate(detection: Detection?) {
    val tracked = kalmanTracker.update(detection)
    _trackedBall.value = tracked
    if (tracked != null) {
        val mapper = (_calibrationState.value as? CalibrationState.Calibrated)?.mapper
        val courtPos = mapper?.mapToCourtCoords(
            PointF(
                tracked.position.x * IMAGE_WIDTH,   // denormalize ke pixel
                tracked.position.y * IMAGE_HEIGHT
            )
        )
        _ballCourtPos.value = courtPos   // StateFlow baru
        val event = bounceDetector.process(tracked.velocity.y, courtPos, tracked.isPredicted)
        if (event is BounceEvent.PointAwarded) handleBounceEvent(event)
    } else {
        _ballCourtPos.value = null
    }
}

companion object {
    const val IMAGE_WIDTH  = 640f
    const val IMAGE_HEIGHT = 480f
}
```

### 3b. Live Ball Position di Minimap

**StateFlow baru di ViewModel:**
```kotlin
private val _ballCourtPos = MutableStateFlow<PointF?>(null)
val ballCourtPos: StateFlow<PointF?> = _ballCourtPos.asStateFlow()
```

**Perubahan `CourtHeatmapView`:**
```kotlin
@Composable
fun CourtHeatmapView(
    heatmapBitmap: Bitmap?,
    bounceCount: Int,
    liveBallCourtPos: PointF? = null,   // tambahan
    modifier: Modifier = Modifier
)
```

Di dalam Canvas, setelah heatmap bitmap:
```kotlin
liveBallCourtPos?.let { pos ->
    val bx = (pos.x / HomographyMapper.COURT_WIDTH_M) * size.width
    val by = (pos.y / HomographyMapper.COURT_LENGTH_M) * size.height
    drawCircle(
        color = Color.Cyan,
        radius = 4.dp.toPx(),
        center = Offset(bx, by)
    )
}
```

**CameraScreen** membaca `viewModel.ballCourtPos.collectAsState()` dan meneruskan ke `CourtHeatmapView`.

---

## Urutan Implementasi

1. **Task 1** — Tambah dependency TFLite Interpreter, hapus Task Vision; buat `YoloV8Detector`; update ViewModel; tulis unit test `YoloV8DetectorTest`
2. **Task 2** — Fix koordinat HomographyMapper di `processBallUpdate`; tambah `ballCourtPos` StateFlow; update `CourtHeatmapView` + `CameraScreen`
3. **Task 3** — Tuning parameter `CourtDetector` + white-line enhancement; tambah state `ManualCalibrating`; update ViewModel + CameraScreen overlay 4-tap

---

## Testing

| Komponen | Test |
|----------|------|
| `YoloV8Detector` | Mock Interpreter output → verify NMS, coord normalization |
| `processBallUpdate` (coord fix) | Verifikasi court pos dalam range 0–10.97m / 0–23.77m |
| `applyManualCalibration` | 4 screen coords → verify homography menghasilkan court coords valid |
| `CourtHeatmapView` | liveBallCourtPos null → tidak ada dot; non-null → dot di posisi benar |

---

## File yang Diubah / Dibuat

| File | Aksi |
|------|------|
| `tracking/YoloV8Detector.kt` | Baru |
| `tracking/BallDetector.kt` | Hapus |
| `tracking/CalibrationState.kt` | Tambah `ManualCalibrating` |
| `tracking/CourtDetector.kt` | Tuning parameter + white-line enhancement |
| `ui/viewmodels/BallTrackingViewModel.kt` | `processBallUpdate` fix + `ballCourtPos` StateFlow + `addManualTap()` + `startManualCalibration()` |
| `ui/screens/CameraScreen.kt` | 4-tap overlay + tombol "Kalibrasi Manual" + pass `ballCourtPos` |
| `ui/components/CourtHeatmapView.kt` | Tambah `liveBallCourtPos` param + dot rendering |
| `app/src/main/assets/yolov8_tennis_ball.tflite` | Download manual dari Roboflow |
| `app/build.gradle` | Ganti dependency |
