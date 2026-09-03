# Ball Tracking — Sub-proyek D: Kalman Filter Trajectory Tracking

**Tanggal:** 2026-09-03
**Status:** Draft
**Sub-proyek:** D dari 5 (A → B → C → D → E)

---

## Tujuan

Menambahkan Kalman filter ke pipeline ball tracking untuk memperhalus output `BallDetector` (Sub-proyek B). Sub-proyek D menghasilkan `TrackedBall` per frame: posisi yang dihaluskan, velocity, flag prediksi (saat bola tidak terdeteksi), dan trajektori penuh seluruh rally. Sub-proyek E (bounce detection + auto-score) mengkonsumsi `velocity` dan `trajectory` dari `TrackedBall`.

Sub-proyek D tidak melakukan bounce detection — itu Sub-proyek E.

---

## Arsitektur

### Alur Data

```
BallDetector.onDetections(List<Detection>)
    → BallTrackingViewModel (camera executor thread)
        → kalmanTracker.update(detections.firstOrNull())
            → TrackedBall? (smoothed position, velocity, isPredicted, trajectory)
        → _trackedBall.value = trackedBall
    → CameraScreen collects trackedBall StateFlow
        → render trail + crosshair overlay
```

`KalmanTracker` adalah pure Kotlin (tidak ada Android/CameraX import) — JVM-testable. Dipanggil dari camera executor thread saja, tidak perlu thread-safe internal.

---

## Komponen

### `TrackedBall` (data class baru)

```kotlin
data class TrackedBall(
    val position: PointF,        // smoothed center (cx, cy), normalized 0-1
    val velocity: PointF,        // (vx, vy) normalized units per frame
    val isPredicted: Boolean,    // true jika frame ini tidak ada deteksi
    val trajectory: List<PointF> // semua posisi sejak reset (seluruh rally)
)
```

### `KalmanTracker` (class baru, pure Kotlin)

State vector 4D: `[cx, cy, vx, vy]`

**Konstanta (Companion):**
```
PROCESS_NOISE_Q  = 1e-4f   // tuning: kepercayaan pada model gerak
MEASUREMENT_NOISE_R = 1e-2f // tuning: kepercayaan pada detector
```

**Inisialisasi (frame pertama setelah reset):**
- `cx = detection.cx`, `cy = detection.cy`, `vx = 0f`, `vy = 0f`
- Covariance P = identity × 1.0f
- Sebelum inisialisasi, `update(null)` return `null`

**Predict step (setiap frame):**
```
x̂ = F · x      (cx += vx, cy += vy, velocity konstan)
P = F · P · Fᵀ + Q
```

Transition matrix F (4×4 konstan):
```
[[1, 0, 1, 0],
 [0, 1, 0, 1],
 [0, 0, 1, 0],
 [0, 0, 0, 1]]
```

**Update step (hanya jika ada deteksi):**
```
y = z - H · x̂          (innovation)
S = H · P · Hᵀ + R      (innovation covariance, 2×2)
K = P · Hᵀ · S⁻¹        (Kalman gain)
x = x̂ + K · y
P = (I - K · H) · P
```

Observation matrix H (2×4): mengobservasi `cx` dan `cy` saja.
Inverse S (2×2): hardcode formula `det = a·d - b·c`.

Jika **tidak ada deteksi**: skip update step, gunakan hasil predict, set `isPredicted = true`.

**Derivasi cx, cy dari `Detection`:**
```
cx = (boundingBox.left + boundingBox.right) / 2f
cy = (boundingBox.top + boundingBox.bottom) / 2f
```

**Output per frame:**
```kotlin
TrackedBall(
    position    = PointF(x[0], x[1]),
    velocity    = PointF(x[2], x[3]),
    isPredicted = noDetectionThisFrame,
    trajectory  = internalTrajectory.toList()  // snapshot dari MutableList internal
)
```

`KalmanTracker` menjaga `private val _trajectory = mutableListOf<PointF>()` secara internal. `update()` append ke list ini dan return snapshot via `toList()`. `reset()` memanggil `_trajectory.clear()`.

**`reset()`:** bersihkan trajectory, set state ke uninitialized. `update(null)` setelah reset return `null`.

### `BallTrackingViewModel` (modified)

Tambahan dari Sub-proyek C:
```kotlin
private val kalmanTracker = KalmanTracker()

private val _trackedBall = MutableStateFlow<TrackedBall?>(null)
val trackedBall: StateFlow<TrackedBall?> = _trackedBall.asStateFlow()

fun resetTrajectory() {
    kalmanTracker.reset()
    _trackedBall.value = null
}
```

BallDetector callback diubah:
```kotlin
val detector = BallDetector(context.applicationContext) { detections ->
    _detections.value = detections
    _trackedBall.value = kalmanTracker.update(detections.firstOrNull())
}
```

### `CameraScreen` (modified)

Di branch `else` (kamera aktif), tambah:
```kotlin
val trackedBall by viewModel.trackedBall.collectAsState()
```

Canvas overlay (di bawah calibration overlay, di atas bounding box):
```kotlin
// Trail
trackedBall?.trajectory?.zipWithNext { a, b ->
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
```

---

## File yang Dibuat / Diubah

| Aksi   | File |
|--------|------|
| Create | `app/src/main/java/com/example/tennisscorer/tracking/TrackedBall.kt` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/KalmanTracker.kt` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt` |
| Create | `app/src/test/java/com/example/tennisscorer/tracking/KalmanTrackerTest.kt` |
| Modify | `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt` |

---

## Testing

### `KalmanTrackerTest` (6 test, JVM)

1. `update with null before init returns null`
2. `first detection initializes position to detection center`
3. `second detection smooths position toward observation`
4. `update with null after init returns isPredicted true`
5. `trajectory accumulates across frames`
6. `reset clears trajectory and null until next detection`

### `BallTrackingViewModelTest` (2 test tambahan, total +2)

7. `trackedBall starts null`
8. `resetTrajectory sets trackedBall to null`

---

## Threading

| Operasi | Thread |
|---|---|
| `kalmanTracker.update()` | camera executor |
| `_trackedBall.value = ...` | camera executor (MutableStateFlow: thread-safe write) |
| `resetTrajectory()` | main thread |
| `kalmanTracker.reset()` | main thread (benign race: camera executor mungkin memproses satu frame lagi sebelum melihat state reset — konsekuensinya satu data point ekstra di trajectory, acceptable) |
| `trackedBall.collectAsState()` | main thread (Compose) |

`KalmanTracker` tidak thread-safe secara internal — desain by intent, karena `update()` hanya dipanggil dari satu thread (camera executor).

---

## Global Constraints

- `KalmanTracker` tidak boleh mengimport package Android — pure Kotlin math
- Semua konstanta tuning di `KalmanTracker.Companion`, bukan magic number
- `trajectory` di `TrackedBall` adalah snapshot immutable (`toList()`) — `KalmanTracker` menjaga internal `MutableList<PointF>`
- Tidak ada batas panjang trajektori — simpan seluruh rally
- `reset()` idempotent: dipanggil berkali-kali hasilnya sama
- Tidak ada inline `Color(0x...)` — gunakan `CyanAccent` dan `Color.Yellow` dari Material theme
- YAGNI: tidak ada akselerasi state, tidak ada multi-ball tracking, tidak ada persistensi trajektori ke disk

---

## Catatan Arsitektur

Sub-proyek E mengkonsumsi `trackedBall.trajectory` untuk bounce detection dan `trackedBall.velocity` untuk prediksi arah bola. Jika `calibrationState` adalah `Calibrated`, Sub-proyek E akan memetakan posisi normalisasi ke koordinat lapangan via `HomographyMapper.mapToCourtCoords(position)`.

`resetTrajectory()` akan dipanggil dari Sub-proyek E setelah setiap rally selesai (bola out/bounce count terpenuhi).
