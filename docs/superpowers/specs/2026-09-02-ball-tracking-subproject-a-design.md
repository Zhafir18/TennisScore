# Ball Tracking — Sub-proyek A: CameraX Integration

**Tanggal:** 2026-09-02
**Status:** Draft
**Sub-proyek:** A dari 5 (A → B → C → D → E)

---

## Tujuan

Menambahkan fondasi kamera ke TennisScorer: live camera preview landscape-mode, runtime permission handling, dan `ImageAnalyzer` pipeline yang siap di-hook oleh Sub-proyek B (YOLOv8 TFLite detection). Sub-proyek A tidak melakukan deteksi bola — hanya menyediakan infrastruktur kamera yang benar.

---

## Arsitektur Keseluruhan (konteks)

Pipeline lengkap ball tracking terdiri dari 5 sub-proyek berurutan:

```
[CameraX] → [YOLOv8 TFLite] → [Kalman Filter] → [Homography] → [Bounce Detector] → [TennisScoreEngine]
   (A)            (B)               (D)               (C)              (E)
```

Sub-proyek A menghasilkan `BallTrackingViewModel` dan `ImageAnalyzer` yang menjadi entry point pipeline ini.

---

## Scope Sub-proyek A

### Yang termasuk
- CameraX dependency setup (core, camera2, lifecycle, view)
- Runtime camera permission request dan handling
- `CameraScreen` — landscape full-screen camera preview
- `BallTrackingViewModel` — mengelola lifecycle kamera dan state permission
- `ImageAnalyzer` — menerima `ImageProxy` per frame, meneruskan ke `FrameAnalyzer` callback
- `FrameAnalyzer` fun interface — kontrak untuk Sub-proyek B
- Navigasi: tombol "Ball Tracking" di `SplashScreen` → `CameraScreen`
- `Screen.BallTracking` sealed object di `Screen.kt`

### Yang tidak termasuk
- Deteksi bola (Sub-proyek B)
- Homography / kalibrasi lapangan (Sub-proyek C)
- Kalman filter (Sub-proyek D)
- Bounce detection / in-out decision (Sub-proyek E)
- Overlay visual apapun selain placeholder text "Camera ready"

---

## Komponen dan Interface

### FrameAnalyzer (fun interface)

```kotlin
package com.example.tennisscorer.tracking

fun interface FrameAnalyzer {
    fun analyze(image: ImageProxy)
}
```

Ini adalah kontrak antara Sub-proyek A dan Sub-proyek B. `ImageProxy` harus di-`close()` oleh implementor setelah selesai diproses.

### BallTrackingViewModel

```kotlin
package com.example.tennisscorer.ui.viewmodels

class BallTrackingViewModel : ViewModel() {
    val permissionGranted: StateFlow<Boolean>
    val cameraError: StateFlow<String?>

    // Single-thread executor untuk CameraX ImageAnalysis (dibuat di ViewModel, shutdown di onCleared)
    val cameraExecutor: ExecutorService

    fun onPermissionResult(granted: Boolean)
    fun setFrameAnalyzer(analyzer: FrameAnalyzer)
    fun clearFrameAnalyzer()
}
```

- `permissionGranted` — false sampai user grant permission, UI bereaksi terhadap ini
- `cameraError` — non-null jika ada error inisialisasi kamera
- `cameraExecutor` — `Executors.newSingleThreadExecutor()`, di-shutdown di `onCleared()` untuk mencegah leak
- `setFrameAnalyzer` / `clearFrameAnalyzer` — dipanggil Sub-proyek B untuk memasang/melepas detector

### ImageAnalyzer

```kotlin
package com.example.tennisscorer.tracking

class ImageAnalyzer(private val onFrame: FrameAnalyzer?) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        onFrame?.analyze(image) ?: image.close()
    }
}
```

Kalau tidak ada `FrameAnalyzer` terpasang, `ImageProxy` langsung di-close (tidak di-drop).

### CameraScreen

```kotlin
@Composable
fun CameraScreen(
    viewModel: BallTrackingViewModel,
    onBack: () -> Unit
)
```

- Full-screen landscape `PreviewView` (CameraX)
- Overlay sederhana: teks "Camera ready" di sudut kiri atas (akan diganti overlay deteksi di Sub-proyek B)
- Tombol "← Kembali" di sudut kiri bawah
- Kalau `permissionGranted = false`: tampilkan layar permission rationale + tombol "Beri Izin"
- Kalau `cameraError != null`: tampilkan pesan error + tombol kembali

---

## Navigasi

### Screen.kt — tambahan
```kotlin
object BallTracking : Screen("ball_tracking")
```

### AppNavigation.kt — tambahan route
```kotlin
composable(Screen.BallTracking.route) {
    val vm: BallTrackingViewModel = viewModel()
    CameraScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() }
    )
}
```

`BallTrackingViewModel` tidak membutuhkan `MatchRepository` atau `TennisScoreEngine` — berdiri sendiri.

### SplashScreen.kt — tambahan tombol
Setelah tombol "Riwayat", tambahkan `OutlinedButton` "Ball Tracking" dengan `onClick = onBallTracking`. Signature SplashScreen berubah menjadi:

```kotlin
fun SplashScreen(
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onBallTracking: () -> Unit
)
```

---

## Dependencies Baru

Tambahkan ke `gradle/libs.versions.toml`:

```toml
[versions]
camerax = "1.4.1"

[libraries]
camerax-core     = { group = "androidx.camera", name = "camera-core",     version.ref = "camerax" }
camerax-camera2  = { group = "androidx.camera", name = "camera-camera2",  version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view     = { group = "androidx.camera", name = "camera-view",     version.ref = "camerax" }
```

Tambahkan ke `app/build.gradle.kts` dependencies block:
```kotlin
implementation(libs.camerax.core)
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
implementation(libs.camerax.view)
```

Tambahkan ke `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

---

## Permission Handling

Android 6+ membutuhkan runtime permission untuk kamera. Alur:

1. User tap "Ball Tracking" di SplashScreen → navigasi ke `CameraScreen`
2. `CameraScreen` cek apakah `CAMERA` permission sudah granted
3. Kalau belum → tampilkan rationale screen dengan tombol "Beri Izin Kamera"
4. Tap tombol → `ActivityResultLauncher<String>` request permission
5. Hasil masuk ke `BallTrackingViewModel.onPermissionResult(granted: Boolean)`
6. Kalau granted → inisialisasi CameraX, tampilkan preview
7. Kalau ditolak permanen → tampilkan pesan "Buka Pengaturan untuk mengizinkan kamera"

---

## ImageAnalysis Setup

CameraX dikonfigurasi dengan dua use case:
- `Preview` — ditampilkan di `PreviewView`
- `ImageAnalysis` — feed ke `ImageAnalyzer`, format `RGBA_8888`, resolution `640×480` atau resolusi kamera terdekat, non-blocking (strategy `KEEP_ONLY_LATEST` agar tidak menumpuk frame)

```kotlin
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(640, 480))
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()
    .also { it.setAnalyzer(cameraExecutor, imageAnalyzer) }
```

Format `RGBA_8888` dipilih karena YOLOv8 TFLite (Sub-proyek B) membutuhkan format ini — konsisten dari awal.

---

## Global Constraints

- **Landscape only** — konsisten dengan seluruh app, `LockScreenOrientation` sudah ada di `AppNavigation`
- **Offline** — tidak ada network call
- **Min SDK 24** — CameraX 1.4.x support min SDK 21, aman
- **Warna** — gunakan token dari `ui/theme/Color.kt`, tidak ada inline `Color(0x...)`
- **Tidak ada logika deteksi** — `ImageAnalyzer` hanya meneruskan frame, tidak memproses

---

## Testing

- **Unit test** `BallTrackingViewModel`: permission state transitions (false → true, false → denied)
- **Unit test** `ImageAnalyzer`: frame di-close jika tidak ada analyzer terpasang; analyzer dipanggil jika terpasang
- Manual smoke test: kamera menyala, preview tampil landscape, tombol kembali berfungsi, permission denied menampilkan pesan yang benar

---

## File yang Diubah / Dibuat

| Aksi | File |
|------|------|
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |
| Modify | `AndroidManifest.xml` |
| Modify | `navigation/Screen.kt` |
| Modify | `navigation/AppNavigation.kt` |
| Modify | `ui/screens/SplashScreen.kt` |
| Create | `tracking/FrameAnalyzer.kt` |
| Create | `tracking/ImageAnalyzer.kt` |
| Create | `ui/viewmodels/BallTrackingViewModel.kt` |
| Create | `ui/screens/CameraScreen.kt` |
| Test | `test/.../BallTrackingViewModelTest.kt` |
| Test | `test/.../ImageAnalyzerTest.kt` |
