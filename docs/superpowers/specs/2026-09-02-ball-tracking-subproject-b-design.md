# Ball Tracking — Sub-proyek B: YOLOv8 TFLite Ball Detection

**Tanggal:** 2026-09-02
**Status:** Draft
**Sub-proyek:** B dari 5 (A → B → C → D → E)

---

## Tujuan

Menambahkan deteksi bola tenis per frame ke pipeline CameraX yang dibangun di Sub-proyek A. Sub-proyek B mengimplementasi `FrameAnalyzer` dengan `BallDetector` yang menjalankan model EfficientDet-Lite0 (TFLite) untuk mendeteksi "sports ball" di setiap frame, lalu menampilkan bounding box overlay di `CameraScreen` sebagai feedback visual.

Sub-proyek B tidak melakukan tracking trajektori — itu Sub-proyek D (Kalman filter). Sub-proyek B hanya menghasilkan `List<Detection>` per frame.

---

## Arsitektur Navigasi Final (konteks)

Dua mode match yang akan ada setelah semua sub-proyek selesai:

```
SplashScreen
├── "Start"         → PlayerInput → ScoreboardScreen (manual tapping — existing, tidak berubah)
└── "Ball Tracking" → PlayerInput → CameraGameScreen (kamera aktif, skor otomatis)  ← Sub-proyek E
```

Sub-proyek B membangun fondasi detection layer (`BallDetector` + overlay). `CameraScreen` saat ini berfungsi sebagai debug/preview screen. Sub-proyek E yang akan mengintegrasikan `TennisScoreEngine.pointWonBy()` dengan output bounce detection dan mewiring navigasi "Ball Tracking" → PlayerInput → CameraGameScreen.

---

## Arsitektur

Pipeline lengkap ball tracking:

```
[CameraX] → [YOLOv8 TFLite] → [Kalman Filter] → [Homography] → [Bounce Detector] → [TennisScoreEngine]
   (A)            (B)               (D)               (C)              (E)
```

Data flow Sub-proyek B:

```
CameraX (ImageProxy)
    → ImageAnalyzer (Sub-proyek A, existing)
        → BallDetector : FrameAnalyzer          ← NEW
            → ObjectDetector (TFLite Task Library)
                → List<Detection> (normalized 0-1 coords)
                    → BallTrackingViewModel.detections: StateFlow
                        → CameraScreen Canvas overlay           ← UPDATED
```

---

## Model

- **File:** `app/src/main/assets/efficientdet_lite0.tflite`
- **Sumber:** TFLite Model Hub (EfficientDet-Lite0, trained on COCO 2017)
- **Ukuran:** ~4 MB
- **Kelas target:** `"sports ball"` (class 37 di COCO — mencakup bola tenis)
- **Inference:** CPU (tidak ada GPU delegate di Sub-proyek B)
- **Score threshold:** `0.4f` — cukup rendah untuk bola yang bergerak cepat/blur
- **Max results:** `5`

Download model dari:
`https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite`
Rename ke `efficientdet_lite0.tflite`, simpan di `app/src/main/assets/`.

---

## Komponen dan Interface

### Detection (data class)

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.RectF

data class Detection(
    val boundingBox: RectF,  // normalized 0-1 (left, top, right, bottom)
    val confidence: Float    // 0-1
)
```

Koordinat dinormalisasi 0-1 supaya overlay tidak bergantung pada resolusi preview atau ukuran layar.

### BallDetector

```kotlin
package com.example.tennisscorer.tracking

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class BallDetector(
    context: Context,
    private val onDetections: (List<Detection>) -> Unit
) : FrameAnalyzer {

    private val objectDetector: ObjectDetector = ObjectDetector.createFromFileAndOptions(
        context,
        "efficientdet_lite0.tflite",
        ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.4f)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        image.close()
        val tensorImage = org.tensorflow.lite.support.image.TensorImage.fromBitmap(bitmap)
        val results = objectDetector.detect(tensorImage)
        val detections = results
            .filter { it.categories.any { c -> c.label == "sports ball" } }
            .map { result ->
                val box = result.boundingBox
                Detection(
                    boundingBox = RectF(
                        box.left / bitmap.width,
                        box.top / bitmap.height,
                        box.right / bitmap.width,
                        box.bottom / bitmap.height
                    ),
                    confidence = result.categories
                        .first { it.label == "sports ball" }.score
                )
            }
        onDetections(detections)
    }

    fun close() {
        objectDetector.close()
    }
}
```

- `image.close()` dipanggil segera setelah bitmap diambil — `ImageProxy` tidak boleh di-hold
- `analyze()` dipanggil dari `cameraExecutor` thread — inference di thread yang sama, tidak blocking UI
- `close()` dipanggil dari `BallTrackingViewModel.onCleared()`

### BallTrackingViewModel (modifikasi)

Tambahan dari Sub-proyek A:

```kotlin
private val _detections = MutableStateFlow<List<Detection>>(emptyList())
val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

private var ballDetector: BallDetector? = null

fun initDetector(context: Context) {
    if (ballDetector != null) return  // idempotent
    val detector = BallDetector(context.applicationContext) { _detections.value = it }
    ballDetector = detector
    setFrameAnalyzer(detector)
}

override fun onCleared() {
    ballDetector?.close()
    super.onCleared()
    cameraExecutor.shutdown()
}
```

`_detections.value = it` thread-safe — `MutableStateFlow.value` assignment aman dari thread manapun.

### CameraScreen (modifikasi)

Di dalam branch `else` (kamera aktif), setelah `LaunchedEffect(lifecycleOwner)` yang bind kamera, tambahkan init detector:

```kotlin
LaunchedEffect(lifecycleOwner) {
    viewModel.initDetector(context.applicationContext)
}
```

Overlay bounding box di atas `PreviewView` (dalam `Box` yang sama):

```kotlin
val detections by viewModel.detections.collectAsState()

// Existing:
AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

// New: Canvas overlay
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
```

---

## Dependencies Baru

Tambahkan ke `gradle/libs.versions.toml`:

```toml
[versions]
tflite-task-vision = "0.4.4"

[libraries]
tflite-task-vision = { group = "org.tensorflow", name = "tensorflow-lite-task-vision", version.ref = "tflite-task-vision" }
```

Tambahkan ke `app/build.gradle.kts`:

```kotlin
implementation(libs.tflite.task.vision)
```

---

## Threading

| Operasi | Thread |
|---|---|
| `BallDetector.analyze()` | `cameraExecutor` (single-thread) |
| `ObjectDetector.detect()` | `cameraExecutor` |
| `_detections.value = ...` | `cameraExecutor` (MutableStateFlow: thread-safe) |
| `Canvas` recompose | Main thread |
| `BallDetector.close()` | Main thread (via `onCleared()`) |

---

## Testing

### Unit Tests (JVM)

`BallTrackingViewModelTest` diperluas — 1 test tambahan:

- `detections starts empty` — assert `vm.detections.value == emptyList()`

`initDetector` tidak dapat diuji di JVM — memanggil `ObjectDetector.createFromFileAndOptions()` yang membutuhkan Android assets context dan model file nyata. Idempotency-nya diverifikasi via manual smoke test.

`BallDetector` tidak diuji JVM — sama dengan alasan di atas. Coverage-nya dari manual smoke test di device.

### Manual Smoke Test

1. Buka CameraScreen → kamera aktif, "Camera ready" terlihat
2. Arahkan ke bola tenis → bounding box `CyanAccent` muncul dengan persentase confidence
3. Pindahkan bola → box mengikuti
4. Tidak ada bola → tidak ada box (tidak ada false-positive konstan)
5. Bola bergerak cepat → box boleh hilang sesaat (normal — ditangani Kalman filter di Sub-proyek D)
6. Navigasi kembali ke SplashScreen → tidak ada crash, kamera berhenti

---

## Global Constraints

- Min SDK 24
- Offline — tidak ada network call
- TFLite Task Vision `0.4.4` — tidak ada GPU delegate
- Score threshold `0.4f`, max results `5` — tidak boleh hardcoded sebagai magic number, gunakan konstanta di `BallDetector`
- Tidak ada inline `Color(0x...)` literal — gunakan `CyanAccent` dari `ui/theme/Color.kt`
- `ImageProxy` selalu di-`close()` sebelum inference
- `objectDetector.close()` dipanggil di `BallDetector.close()` → dipanggil dari `onCleared()`
- YAGNI — tidak ada GPU delegate, tidak ada runtime threshold slider, tidak ada multi-class detection

---

## File yang Diubah / Dibuat

| Aksi | File |
|------|------|
| Add | `app/src/main/assets/efficientdet_lite0.tflite` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/Detection.kt` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/BallDetector.kt` |
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt` |
| Modify | `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt` |
