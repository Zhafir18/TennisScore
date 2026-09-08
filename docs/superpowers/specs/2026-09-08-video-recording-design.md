# Video Recording Design Spec

**Date:** 2026-09-08
**Feature:** Match video recording — CameraX VideoCapture + MediaStore output

## Goal

Tambahkan tombol Rekam di CameraScreen yang memungkinkan user merekam video match (dengan audio) dan menyimpannya ke galeri HP. Tidak ada in-app playback — video bisa dibuka dari app Gallery bawaan.

---

## Architecture

### Dependencies

Tambah satu artifact ke `app/build.gradle.kts` dan `gradle/libs.versions.toml`:

```
androidx.camera:camera-video:1.4.1  (versi sama dengan CameraX yang sudah ada)
```

### Permissions

Tambah ke `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

`RECORD_AUDIO` di-request bersamaan dengan `CAMERA` di `LaunchedEffect` awal CameraScreen. Jika ditolak user, recording tetap jalan tanpa audio — tidak ada pesan error, audio adalah bonus bukan syarat.

Tidak butuh `WRITE_EXTERNAL_STORAGE` — simpan via `MediaStore` (Android 10+).

### Files yang Berubah

| File | Perubahan |
|---|---|
| `gradle/libs.versions.toml` | Tambah alias `camerax-video` |
| `app/build.gradle.kts` | Tambah `implementation(libs.camerax.video)` |
| `AndroidManifest.xml` | Tambah `RECORD_AUDIO` permission |
| `BallTrackingViewModel.kt` | Tambah recording state + methods |
| `CameraScreen.kt` | Bind `VideoCapture` + tombol record UI |
| `BallTrackingViewModelTest.kt` | Tambah 4 test baru |

**Tidak ada file baru.**

---

## BallTrackingViewModel

### State Baru

```kotlin
val isRecording: StateFlow<Boolean>     // true saat rekam aktif
val recordingError: StateFlow<String?>  // pesan error finalize, null = oke
val videoCapture: VideoCapture?         // null jika device tidak support
```

### Fields Internal

```kotlin
private val recorder: Recorder          // dikonfigurasi sekali saat init
private var activeRecording: Recording? // null = tidak sedang rekam
```

`Recorder` dan `VideoCapture` dibuat satu kali saat init ViewModel:

```kotlin
val recorder = Recorder.Builder()
    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
    .build()
val videoCapture = VideoCapture.withOutput(recorder)
```

### `startRecording(context: Context)`

1. Buat `MediaStoreOutputOptions`:
   - `DISPLAY_NAME`: `Tennis_<System.currentTimeMillis()>.mp4`
   - `MIME_TYPE`: `video/mp4`
   - `RELATIVE_PATH`: `Movies/TennisScorer`
2. Cek `RECORD_AUDIO` permission — jika granted panggil `.withAudioEnabled()`
3. `.start(cameraExecutor, eventListener)` → assign ke `activeRecording`
4. `isRecording = true`

### `stopRecording()`

1. `activeRecording?.stop()`
2. `activeRecording = null`
3. `isRecording = false`

### `setVideoUnavailable()`

Set `_videoCapture = null` — dipanggil dari CameraScreen jika `bindToLifecycle` gagal.

### Event Listener

```
VideoRecordEvent.Start    → konfirmasi isRecording = true
VideoRecordEvent.Finalize → isRecording = false
                            jika hasError() → recordingError = "Rekaman gagal disimpan"
```

### `onCleared()`

Tambah `activeRecording?.stop()` sebelum `cameraExecutor.shutdown()` — pastikan file tidak corrupt jika user keluar saat rekam aktif.

---

## CameraScreen

### Binding VideoCapture

```kotlin
cameraProvider.bindToLifecycle(
    lifecycleOwner,
    CameraSelector.DEFAULT_BACK_CAMERA,
    preview, imageAnalysis, viewModel.videoCapture  // tambah videoCapture
)
```

Jika `bindToLifecycle` throw (device tidak support 3 use case serentak):
- Catch exception
- Panggil `viewModel.setVideoUnavailable()`
- Camera tetap jalan dengan `Preview + ImageAnalysis` saja (tidak crash)

### Permission Request

`CAMERA` dan `RECORD_AUDIO` di-request bersamaan via `ActivityResultContracts.RequestMultiplePermissions`. Ganti `RequestPermission` (single) yang sudah ada dengan `RequestMultiplePermissions`. Result map dipisah: key `CAMERA` → update `permissionGranted`, key `RECORD_AUDIO` → simpan di `audioGranted` local variable (dipakai `startRecording()` untuk putuskan `.withAudioEnabled()`).

### Tombol Record

Posisi: baris bawah, di antara tombol "← Kembali" dan pojok kanan.

```
[ ← Kembali ]  [ ⏺ Rekam ]          ← idle
[ ← Kembali ]  [ ⏹ Stop  00:42 ]    ← recording (merah)
```

- Tombol tidak muncul jika `viewModel.videoCapture == null`
- Timer `mm:ss` — increment tiap detik via `LaunchedEffect(isRecording)`
- Warna tombol rekam: `Color.Red` (recording) / `ActionBtnBg` (idle)

### Error Display

Jika `recordingError != null` → tampilkan teks kuning kecil di atas tombol, auto-clear setelah 3 detik via `LaunchedEffect(recordingError)`.

---

## Testing

### Unit Test Baru (BallTrackingViewModelTest.kt)

```
isRecording starts false
recordingError starts null
stopRecording when not recording does not crash
setVideoUnavailable makes videoCapture null
```

`startRecording()` dan `stopRecording()` saat aktif tidak di-unit-test — butuh CameraX hardware.

### Manual Testing Checklist

- [ ] Tap Rekam → tombol berubah merah + timer jalan
- [ ] Tap Stop → video MP4 muncul di Gallery → folder `Movies/TennisScorer`
- [ ] Keluar CameraScreen saat rekam aktif → file tidak corrupt
- [ ] Tolak izin `RECORD_AUDIO` → recording tetap jalan (tanpa audio)
- [ ] Device tidak support 3 use case → tombol Rekam tidak muncul, camera tetap jalan

---

## Global Constraints

- Tidak ada in-app video playback
- Tidak ada UI khusus untuk izin `RECORD_AUDIO` — graceful degrade ke no-audio
- Tidak ada limit durasi rekam
- Tidak ada fitur pause recording
- File disimpan ke `Movies/TennisScorer/` — tidak bisa dikonfigurasi user
