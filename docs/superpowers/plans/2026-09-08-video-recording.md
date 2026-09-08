# Video Recording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tambahkan tombol Rekam di CameraScreen yang menyimpan video match (dengan audio opsional) ke galeri HP via CameraX VideoCapture + MediaStore.

**Architecture:** `BallTrackingViewModel` menginisialisasi `VideoCapture<Recorder>` secara lazy via `initVideoCapture()`, mengekspos `isRecording`/`isVideoAvailable`/`recordingError` sebagai StateFlow, dan menyimpan output ke `Movies/TennisScorer/` via `MediaStoreOutputOptions`. `CameraScreen` bind `VideoCapture` use case bersama `Preview + ImageAnalysis` dengan fallback ke 2 use case jika device tidak support, dan menampilkan tombol Rekam + timer.

**Tech Stack:** CameraX `camera-video:1.4.1`, `MediaStoreOutputOptions`, `ActivityResultContracts.RequestMultiplePermissions`, Kotlin coroutines (`delay`)

## Global Constraints

- `camera-video` versi = `1.4.1` (sama dengan CameraX yang sudah ada)
- Output video: `Movies/TennisScorer/Tennis_<timestamp>.mp4`, MIME `video/mp4`
- Tidak ada in-app playback — video hanya tersimpan ke galeri
- Jika `RECORD_AUDIO` ditolak: rekam tetap jalan tanpa audio, tidak ada pesan error
- Jika device tidak support 3 use case serentak: `setVideoUnavailable()` → sembunyikan tombol record → camera tetap jalan dengan 2 use case
- `VideoCapture` diinisialisasi lazy via `initVideoCapture()` — tidak di constructor ViewModel (CameraX butuh hardware)
- Tidak ada fitur pause, tidak ada limit durasi rekam

---

### Task 1: Dependencies + Permissions

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: alias `libs.camerax.video` tersedia untuk task selanjutnya

- [ ] **Step 1: Tambah alias `camerax-video` ke `gradle/libs.versions.toml`**

Tambah baris ini setelah baris `camerax-view`:

```toml
camerax-video    = { group = "androidx.camera", name = "camera-video",    version.ref = "camerax" }
```

- [ ] **Step 2: Tambah dependency ke `app/build.gradle.kts`**

Tambah baris ini setelah `implementation(libs.camerax.view)`:

```kotlin
implementation(libs.camerax.video)
```

- [ ] **Step 3: Tambah RECORD_AUDIO permission ke `app/src/main/AndroidManifest.xml`**

Tambah baris ini setelah `<uses-permission android:name="android.permission.CAMERA" />`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- [ ] **Step 4: Build verifikasi — tidak ada compile error**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat(video): add camera-video dependency and RECORD_AUDIO permission"
```

---

### Task 2: BallTrackingViewModel — recording state + methods

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`

**Interfaces:**
- Consumes: `libs.camerax.video` (Task 1)
- Produces:
  - `BallTrackingViewModel.isRecording: StateFlow<Boolean>`
  - `BallTrackingViewModel.isVideoAvailable: StateFlow<Boolean>`
  - `BallTrackingViewModel.recordingError: StateFlow<String?>`
  - `BallTrackingViewModel.videoCapture: VideoCapture<Recorder>?`
  - `BallTrackingViewModel.initVideoCapture()`
  - `BallTrackingViewModel.setVideoUnavailable()`
  - `BallTrackingViewModel.startRecording(context: Context, audioGranted: Boolean)`
  - `BallTrackingViewModel.stopRecording()`
  - `BallTrackingViewModel.clearRecordingError()`

- [ ] **Step 1: Tulis failing tests — tambah 4 test baru di akhir class `BallTrackingViewModelTest`**

```kotlin
@Test fun `isRecording starts false`() {
    assertFalse(vm.isRecording.value)
}

@Test fun `recordingError starts null`() {
    assertNull(vm.recordingError.value)
}

@Test fun `isVideoAvailable starts false`() {
    assertFalse(vm.isVideoAvailable.value)
}

@Test fun `stopRecording when not recording does not crash`() {
    vm.stopRecording()
    assertFalse(vm.isRecording.value)
}
```

- [ ] **Step 2: Jalankan test baru — verifikasi FAIL**

```
./gradlew :app:testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: FAIL karena `isRecording`, `recordingError`, `isVideoAvailable` belum ada.

- [ ] **Step 3: Tambah imports ke `BallTrackingViewModel.kt`**

Tambah setelah import yang sudah ada:

```kotlin
import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
```

- [ ] **Step 4: Tambah state fields recording ke `BallTrackingViewModel.kt`**

Tambah setelah blok `_bounceCount`/`bounceCount` yang sudah ada:

```kotlin
private val _isRecording = MutableStateFlow(false)
val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

private val _recordingError = MutableStateFlow<String?>(null)
val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

private val _isVideoAvailable = MutableStateFlow(false)
val isVideoAvailable: StateFlow<Boolean> = _isVideoAvailable.asStateFlow()

private var _videoCapture: VideoCapture<Recorder>? = null
val videoCapture: VideoCapture<Recorder>? get() = _videoCapture

private var activeRecording: Recording? = null
```

- [ ] **Step 5: Tambah recording methods ke `BallTrackingViewModel.kt`**

Tambah setelah method `resetTrajectory()`:

```kotlin
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
```

- [ ] **Step 6: Update `onCleared()` — stop active recording sebelum executor shutdown**

Ganti seluruh `override fun onCleared()`:

```kotlin
override fun onCleared() {
    activeRecording?.stop()
    backgroundScope.cancel()
    cameraExecutor.shutdown()
    courtDetector?.close()
    ballDetector?.close()
    super.onCleared()
}
```

- [ ] **Step 7: Jalankan test — verifikasi PASS**

```
./gradlew :app:testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: semua PASS (13 test lama + 4 test baru = 17 total)

- [ ] **Step 8: Jalankan semua unit test — verifikasi tidak ada regresi**

```
./gradlew :app:testDebugUnitTest
```

Expected: semua PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt
git add app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
git commit -m "feat(video): add recording state and methods to BallTrackingViewModel"
```

---

### Task 3: CameraScreen — bind VideoCapture + record button UI

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`

**Interfaces:**
- Consumes:
  - `BallTrackingViewModel.isRecording: StateFlow<Boolean>` (Task 2)
  - `BallTrackingViewModel.isVideoAvailable: StateFlow<Boolean>` (Task 2)
  - `BallTrackingViewModel.recordingError: StateFlow<String?>` (Task 2)
  - `BallTrackingViewModel.videoCapture: VideoCapture<Recorder>?` (Task 2)
  - `BallTrackingViewModel.initVideoCapture()` (Task 2)
  - `BallTrackingViewModel.setVideoUnavailable()` (Task 2)
  - `BallTrackingViewModel.startRecording(context: Context, audioGranted: Boolean)` (Task 2)
  - `BallTrackingViewModel.stopRecording()` (Task 2)
  - `BallTrackingViewModel.clearRecordingError()` (Task 2)

- [ ] **Step 1: Tambah import `kotlinx.coroutines.delay` ke `CameraScreen.kt`**

Tambah setelah blok import yang sudah ada:

```kotlin
import kotlinx.coroutines.delay
```

- [ ] **Step 2: Ganti permission launcher (single → multi) di `CameraScreen.kt`**

Ganti blok berikut (baris 56–68):

```kotlin
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
```

Dengan:

```kotlin
var audioGranted by remember { mutableStateOf(false) }

val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    viewModel.onPermissionResult(permissions[Manifest.permission.CAMERA] == true)
    audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
}

LaunchedEffect(lifecycleOwner) {
    val cameraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val audioOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (cameraOk) {
        viewModel.onPermissionResult(true)
        audioGranted = audioOk
    } else {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }
}
```

- [ ] **Step 3: Update tombol "Beri Izin Kamera" — launch kedua permission sekaligus**

Di dalam blok `!permissionGranted`, ganti:

```kotlin
permissionLauncher.launch(Manifest.permission.CAMERA)
```

Dengan:

```kotlin
permissionLauncher.launch(
    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
)
```

- [ ] **Step 4: Tambah state recording di dalam blok `else ->`**

Di dalam blok `else ->`, setelah baris `val bounceCount by viewModel.bounceCount.collectAsState()`, tambah:

```kotlin
val isRecording by viewModel.isRecording.collectAsState()
val isVideoAvailable by viewModel.isVideoAvailable.collectAsState()
val recordingError by viewModel.recordingError.collectAsState()

var recordingSeconds by remember { mutableIntStateOf(0) }
LaunchedEffect(isRecording) {
    if (isRecording) {
        recordingSeconds = 0
        while (true) {
            delay(1000)
            recordingSeconds++
        }
    } else {
        recordingSeconds = 0
    }
}

LaunchedEffect(recordingError) {
    if (recordingError != null) {
        delay(3000)
        viewModel.clearRecordingError()
    }
}
```

- [ ] **Step 5: Ganti LaunchedEffect kamera — tambah VideoCapture + fallback**

Ganti seluruh `LaunchedEffect(lifecycleOwner) { val cameraProviderFuture = ... }` (baris 212–240) dengan:

```kotlin
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

            viewModel.initVideoCapture()
            cameraProvider.unbindAll()

            val vc = viewModel.videoCapture
            try {
                val useCases = listOfNotNull(preview, imageAnalysis, vc).toTypedArray()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases
                )
            } catch (e: Exception) {
                if (vc != null) {
                    viewModel.setVideoUnavailable()
                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, imageAnalysis
                        )
                    } catch (e2: Exception) {
                        viewModel.onCameraError("Kamera tidak dapat dibuka: ${e2.message}")
                        return@addListener
                    }
                } else {
                    viewModel.onCameraError("Kamera tidak dapat dibuka: ${e.message}")
                    return@addListener
                }
            }

            viewModel.initCalibration(context.applicationContext)
        } catch (e: Exception) {
            viewModel.onCameraError("Kamera tidak dapat dibuka: ${e.message}")
        }
    }, ContextCompat.getMainExecutor(context))
}
```

- [ ] **Step 6: Ganti tombol "← Kembali" dengan Row + record button + error display**

Ganti blok `Button(onClick = onBack, ...)` di akhir blok `else ->`:

```kotlin
recordingError?.let { error ->
    Text(
        text = "⚠ $error",
        color = Color.Yellow,
        fontSize = 11.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 72.dp)
    )
}

Row(
    modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Button(
        onClick = onBack,
        colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
    ) {
        Text("← Kembali", color = Color.White, fontSize = 12.sp)
    }

    if (isVideoAvailable) {
        val timerText = "%02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)
        Button(
            onClick = {
                if (isRecording) viewModel.stopRecording()
                else viewModel.startRecording(context, audioGranted)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else ActionBtnBg
            )
        ) {
            Text(
                text = if (isRecording) "⏹ Stop $timerText" else "⏺ Rekam",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
```

- [ ] **Step 7: Jalankan semua unit test — verifikasi tidak ada regresi**

```
./gradlew :app:testDebugUnitTest
```

Expected: semua PASS

- [ ] **Step 8: Build debug APK — verifikasi tidak ada compile error**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt
git commit -m "feat(video): CameraScreen — bind VideoCapture, record button + timer"
```

---

## Self-Review Checklist

- [ ] `camera-video` dependency + `RECORD_AUDIO` permission — Task 1
- [ ] `isRecording`, `isVideoAvailable`, `recordingError` StateFlow — Task 2
- [ ] `initVideoCapture()`, `setVideoUnavailable()`, `startRecording()`, `stopRecording()`, `clearRecordingError()` — Task 2
- [ ] `activeRecording?.stop()` di `onCleared()` sebelum executor shutdown — Task 2 Step 6
- [ ] Permission launcher ganti ke `RequestMultiplePermissions` — Task 3 Step 2–3
- [ ] Fallback bind 2 use case jika 3 tidak support — Task 3 Step 5
- [ ] Tombol record + timer `mm:ss` + error display auto-clear 3 detik — Task 3 Step 4+6
- [ ] `audioGranted` flag dipakai `startRecording()` — Task 3 Step 2+6
- [ ] Type consistency: `VideoCapture<Recorder>` konsisten di Task 2 dan Task 3
