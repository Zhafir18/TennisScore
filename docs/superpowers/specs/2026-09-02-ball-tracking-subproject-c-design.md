# Ball Tracking — Sub-proyek C: Homography Court Calibration

**Tanggal:** 2026-09-02
**Status:** Draft
**Sub-proyek:** C dari 5 (A → B → C → D → E)

---

## Tujuan

Menambahkan kalibrasi lapangan otomatis ke pipeline ball tracking. Sub-proyek C mendeteksi garis lapangan tenis dari frame kamera menggunakan OpenCV (Canny edge detection + HoughLinesP), menghitung matriks homografi yang memetakan koordinat piksel ke koordinat lapangan nyata (dalam meter), dan mengekspos state kalibrasi ke `CameraScreen`.

Sub-proyek C tidak melakukan tracking trajektori bola — itu Sub-proyek D. Sub-proyek C hanya menghasilkan `HomographyMapper` yang siap dipakai Sub-proyek D dan E untuk konversi koordinat.

---

## Arsitektur

### Pola: Swap FrameAnalyzer

Saat `CameraScreen` buka dan kamera bind, ViewModel memasang `CourtDetector` sebagai FrameAnalyzer aktif. Setelah kalibrasi selesai (berhasil atau gagal setelah timeout), ViewModel swap ke `BallDetector`. Hanya satu FrameAnalyzer aktif di satu waktu — tidak ada pemrosesan ganda.

```
CameraScreen buka
    → initCalibration(context)
        → CourtDetector dipasang sebagai FrameAnalyzer
        → calibrationState = Calibrating
            → [frame demi frame, maks 60 frame]
                → Berhasil: calibrationState = Calibrated(HomographyMapper)
                             swap ke BallDetector
                → Gagal:    calibrationState = Failed(reason)
                             swap ke BallDetector (ball detection tetap jalan)
```

### CalibrationState

```kotlin
sealed class CalibrationState {
    object Uncalibrated : CalibrationState()
    object Calibrating : CalibrationState()
    data class Calibrated(val mapper: HomographyMapper) : CalibrationState()
    data class Failed(val reason: String) : CalibrationState()
}
```

### File yang Dibuat / Diubah

| Aksi   | File |
|--------|------|
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/CalibrationState.kt` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/HomographyResult.kt` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/CourtDetector.kt` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/HomographyMapper.kt` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt` |
| Modify | `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt` |
| Create | `app/src/test/java/com/example/tennisscorer/tracking/HomographyMapperTest.kt` |

---

## Komponen

### HomographyResult (internal)

Tipe callback internal dari `CourtDetector` ke `BallTrackingViewModel`:

```kotlin
package com.example.tennisscorer.tracking

sealed class HomographyResult {
    data class Success(val matrix: FloatArray) : HomographyResult()  // 3x3 row-major
    data class Failed(val reason: String) : HomographyResult()
}
```

### CourtDetector

Mengimplementasi `FrameAnalyzer`. Memproses frame satu per satu dengan OpenCV hingga court terdeteksi atau timeout.

```kotlin
package com.example.tennisscorer.tracking

class CourtDetector(
    private val onResult: (HomographyResult) -> Unit
) : FrameAnalyzer {

    companion object {
        const val MAX_FRAMES = 60
    }

    private var framesProcessed = 0
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) { image.close(); return }
        val bitmap = image.toBitmap()
        image.close()
        framesProcessed++

        val matrix = tryDetectCourt(bitmap)
        if (matrix != null) {
            done = true
            onResult(HomographyResult.Success(matrix))
        } else if (framesProcessed >= MAX_FRAMES) {
            done = true
            onResult(HomographyResult.Failed("Lapangan tidak terdeteksi setelah $MAX_FRAMES frame"))
        }
    }

    fun close() { /* Mat sudah di-release per frame — tidak ada state persisten */ }

    private fun tryDetectCourt(bitmap: Bitmap): FloatArray? { ... }
}
```

**OpenCV pipeline di `tryDetectCourt()`:**

```
Bitmap
  → Utils.bitmapToMat(bitmap, rgbaMat)
  → Imgproc.cvtColor(rgbaMat, grayMat, COLOR_RGBA2GRAY)
  → Imgproc.Canny(grayMat, edgesMat, 50.0, 150.0)
  → Imgproc.HoughLinesP(edgesMat, linesMat,
        rho=1.0, theta=PI/180, threshold=80,
        minLineLength=100.0, maxLineGap=10.0)
  → parse linesMat → List<Line(x1,y1,x2,y2,angle)>
  → filter near-horizontal: |θ| < 20° → ambil 2 terpanjang
  → filter near-vertical: |θ - 90°| < 20° → ambil 2 terpanjang
  → jika < 2 horizontal atau < 2 vertikal → return null
  → hitung 4 titik perpotongan (corners)
  → sanity check: luas quadrilateral > 15% (frameWidth × frameHeight)
  → jika gagal sanity check → return null
  → src = MatOfPoint2f(4 pixel corners, urutan: BL, BR, TL, TR)
  → dst = MatOfPoint2f(courtDst, sesuai urutan)
  → H = Imgproc.getPerspectiveTransform(src, dst)  // Mat CV_64F, 3×3
  → ekstrak 9 double dari H → convert ke FloatArray(9)
  → release semua Mat (rgbaMat, grayMat, edgesMat, linesMat, H)
  → return FloatArray(9)
```

**Urutan corners dan koordinat lapangan (dst):**

Asumsi kamera dipasang di sisi samping lapangan, orientasi portrait:
- bottom-left image → `(0f, 0f)` — near-left corner (doubles sideline)
- bottom-right image → `(COURT_WIDTH_M, 0f)` — near-right corner
- top-left image → `(0f, COURT_LENGTH_M)` — far-left corner
- top-right image → `(COURT_WIDTH_M, COURT_LENGTH_M)` — far-right corner

### HomographyMapper

Pure Kotlin — tidak ada dependency OpenCV. JVM-testable.

```kotlin
package com.example.tennisscorer.tracking

import android.graphics.PointF

class HomographyMapper(private val matrix: FloatArray) {
    // matrix: FloatArray(9), 3×3 row-major homography

    fun mapToCourtCoords(normalizedPoint: PointF): PointF {
        val x = normalizedPoint.x
        val y = normalizedPoint.y
        val w = matrix[6] * x + matrix[7] * y + matrix[8]
        return PointF(
            (matrix[0] * x + matrix[1] * y + matrix[2]) / w,
            (matrix[3] * x + matrix[4] * y + matrix[5]) / w
        )
    }

    companion object {
        const val COURT_WIDTH_M = 10.97f    // doubles sideline to sideline
        const val COURT_LENGTH_M = 23.77f   // baseline to baseline
    }
}
```

Input `normalizedPoint`: center dari `Detection.boundingBox` dalam koordinat 0-1.
Output: `PointF(x, y)` dalam meter di sistem koordinat lapangan.

### BallTrackingViewModel (modifikasi)

Tambahan dari Sub-proyek B:

```kotlin
private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Uncalibrated)
val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

private var courtDetector: CourtDetector? = null

fun initCalibration(context: Context) {
    if (_calibrationState.value != CalibrationState.Uncalibrated) return  // idempotent
    val appContext = context.applicationContext
    _calibrationState.value = CalibrationState.Calibrating
    val detector = CourtDetector { result ->
        when (result) {
            is HomographyResult.Success ->
                _calibrationState.value = CalibrationState.Calibrated(HomographyMapper(result.matrix))
            is HomographyResult.Failed ->
                _calibrationState.value = CalibrationState.Failed(result.reason)
        }
        initDetector(appContext)  // swap ke BallDetector
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
```

`initCalibration` menggantikan call `initDetector` dari `CameraScreen`. `initDetector` (Sub-proyek B) tidak berubah — masih dipanggil internal setelah kalibrasi.

### CameraScreen (modifikasi)

Di branch `else` (kamera aktif):

1. Ganti `viewModel.initDetector(...)` → `viewModel.initCalibration(...)` di try block.

2. Collect `calibrationState`:
```kotlin
val calibrationState by viewModel.calibrationState.collectAsState()
```

3. Tambah overlay di atas Canvas bounding box:
```kotlin
when (calibrationState) {
    is CalibrationState.Uncalibrated,
    is CalibrationState.Calibrating -> {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = CyanAccent)
                Spacer(Modifier.height(12.dp))
                Text("Mendeteksi lapangan...", color = Color.White, fontSize = 14.sp)
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
    is CalibrationState.Calibrated -> { /* tidak ada overlay tambahan */ }
}
```

---

## Dependencies Baru

`gradle/libs.versions.toml`:
```toml
[versions]
opencv = "4.9.0"

[libraries]
opencv = { group = "org.opencv", name = "opencv", version.ref = "opencv" }
```

`app/build.gradle.kts`:
```kotlin
implementation(libs.opencv)
```

---

## Threading

| Operasi | Thread |
|---|---|
| `CourtDetector.analyze()` | `cameraExecutor` |
| OpenCV Canny / HoughLinesP | `cameraExecutor` |
| `_calibrationState.value = ...` | `cameraExecutor` (MutableStateFlow: thread-safe) |
| `HomographyMapper.mapToCourtCoords()` | caller thread |
| `CourtDetector.close()` | Main thread (via `onCleared()`) |

---

## Testing

### Unit Tests (JVM)

**`HomographyMapperTest`** — 3 test:
- `identity-like matrix maps origin to origin`
- `known 3x3 matrix maps center point to expected court coords`
- `COURT_WIDTH_M and COURT_LENGTH_M match standard doubles dimensions`

**`BallTrackingViewModelTest`** — 3 test tambahan (total bertambah dari 18):
- `calibrationState starts Uncalibrated`
- `initCalibration sets state to Calibrating`
- `initCalibration is idempotent — second call ignored`

### Manual Smoke Test

1. Buka CameraScreen → overlay "Mendeteksi lapangan..." + spinner muncul
2. Arahkan ke lapangan tenis dari samping → dalam ~2 detik overlay hilang, ball detection aktif
3. Arahkan ke non-lapangan → setelah 60 frame, teks kuning "Kalibrasi gagal" muncul, ball detection tetap jalan
4. Gerakkan bola saat kalibrasi gagal → bounding box `CyanAccent` tetap muncul (Sub-proyek B tidak terpengaruh)
5. Navigasi kembali → tidak ada crash, tidak ada memory leak

---

## Global Constraints

- Min SDK 24
- Offline — tidak ada network call
- OpenCV `4.9.0` via Maven Central (`org.opencv:opencv:4.9.0`)
- `HomographyMapper` tidak boleh mengimport OpenCV — pure Kotlin math
- Semua OpenCV `Mat` wajib di-`release()` di `tryDetectCourt()` sebelum return (termasuk branch null)
- `CourtDetector.done` flag mencegah pemrosesan frame setelah callback dipanggil
- `MAX_FRAMES = 60` — konstanta di `CourtDetector.Companion`, bukan magic number
- `COURT_WIDTH_M = 10.97f`, `COURT_LENGTH_M = 23.77f` — konstanta di `HomographyMapper.Companion`
- `initCalibration` idempotent: jika `calibrationState != Uncalibrated`, langsung return
- Tidak ada inline `Color(0x...)` literal — gunakan `CyanAccent` dari theme
- YAGNI: tidak ada re-kalibrasi manual, tidak ada court outline overlay, tidak ada GPU OpenCV delegate

---

## Catatan Arsitektur

Sub-proyek D (Kalman filter) mengkonsumsi `Detection.boundingBox` center langsung — tidak perlu `HomographyMapper` untuk tracking.

Sub-proyek E (bounce detection + auto-score) mengkonsumsi `HomographyMapper` dari `calibrationState` (jika `Calibrated`) untuk menentukan apakah bounce dalam atau luar lapangan. Jika `calibrationState` adalah `Failed`, Sub-proyek E menonaktifkan fitur in/out detection sementara.
