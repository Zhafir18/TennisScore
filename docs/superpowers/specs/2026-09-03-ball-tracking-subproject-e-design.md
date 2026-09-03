# Ball Tracking — Sub-proyek E: Bounce Detection, In/Out, Auto-Score

**Tanggal:** 2026-09-03
**Status:** Draft
**Sub-proyek:** E dari 5 (A → B → C → D → E)

---

## Tujuan

Menambahkan bounce detection ke pipeline ball tracking. Sub-proyek E mengkonsumsi `TrackedBall` dari Sub-proyek D (Kalman filter) dan `HomographyMapper` dari Sub-proyek C (homography calibration) untuk:

1. Mendeteksi kapan bola memantul di lapangan
2. Menentukan apakah pantul IN atau OUT
3. Menerapkan aturan rally: poin hanya diberikan pada pantul **kedua** di sisi yang sama (tanpa bola kembali ke sisi lawan) atau bola OUT
4. Memanggil `TennisScoreEngine.pointWonBy()` secara otomatis

Sub-proyek E tidak melakukan deteksi servis atau aturan kotak servis — in-bounds = seluruh area court.

---

## Arsitektur

### Alur Data

```
BallDetector callback (camera executor thread)
  → kalmanTracker.update(detection) → TrackedBall
  → (calibrationState as? Calibrated)?.mapper?.mapToCourtCoords(ball.position) → courtPos
  → bounceDetector.process(ball.velocity.y, courtPos, ball.isPredicted) → BounceEvent?
      → null: rally lanjut, tidak ada aksi
      → PointAwarded(winner, isOut, courtPos):
          engine.pointWonBy(winner)
          kalmanTracker.reset()
          bounceDetector.reset()
          _trackedBall.value = null
```

`BounceDetector` menerima primitive (`vy: Float`, `courtPos: PointF?`, `isPredicted: Boolean`) — bukan `HomographyMapper` langsung. `BallTrackingViewModel` yang melakukan mapping. Ini membuat `BounceDetector` pure Kotlin math, JVM-testable tanpa Android stub.

---

## Komponen

### `BounceEvent` (sealed class baru)

```kotlin
sealed class BounceEvent {
    data class PointAwarded(
        val winner: Int,        // 1 atau 2
        val isOut: Boolean,     // true jika bola keluar court
        val courtPos: PointF    // posisi bounce dalam meter (court coords)
    ) : BounceEvent()
}
```

### `BounceDetector` (class baru, pure Kotlin)

**Konstanta (Companion):**
```kotlin
const val BOUNCE_COOLDOWN_FRAMES = 10   // frame cooldown setelah deteksi bounce
const val VELOCITY_THRESHOLD     = 0.005f  // dead-zone noise filter untuk vy
val NET_Y_M = HomographyMapper.COURT_LENGTH_M / 2f  // 11.885m
```

**State internal:**
```kotlin
private var previousVy     = 0f   // vy frame sebelumnya
private var cooldownFrames = 0    // sisa frame cooldown
private var lastSide       = 0    // 0=unknown, 1=sisi P1 (y<NET), 2=sisi P2 (y>NET)
private var bounceCountP1  = 0    // pantul di sisi P1 dalam possession ini
private var bounceCountP2  = 0    // pantul di sisi P2 dalam possession ini
```

**Logic `process(vy: Float, courtPos: PointF?, isPredicted: Boolean): BounceEvent?`:**

```
// Step 1: Net crossing detection (setiap frame)
if courtPos != null:
    currentSide = if courtPos.y < NET_Y_M then 1 else 2
    if currentSide != lastSide && lastSide != 0:
        // Bola masuk sisi baru → reset bounce count sisi itu
        if currentSide == 1: bounceCountP1 = 0
        else: bounceCountP2 = 0
    lastSide = currentSide

// Step 2: Cooldown & predicted guard
if isPredicted || cooldownFrames > 0:
    cooldownFrames = max(0, cooldownFrames - 1)
    previousVy = vy
    return null

// Step 3: Bounce detection (sign change)
bounce = previousVy > VELOCITY_THRESHOLD && vy < -VELOCITY_THRESHOLD
previousVy = vy
if !bounce || courtPos == null: return null

cooldownFrames = BOUNCE_COOLDOWN_FRAMES
isIn = courtPos.x in [0, COURT_WIDTH_M] && courtPos.y in [0, COURT_LENGTH_M]

// Step 4: Scoring
if isIn:
    if courtPos.y < NET_Y_M:
        bounceCountP1++
        if bounceCountP1 >= 2: return PointAwarded(winner=2, isOut=false, courtPos)
        else: return null   // pantul pertama, rally lanjut
    else:
        bounceCountP2++
        if bounceCountP2 >= 2: return PointAwarded(winner=1, isOut=false, courtPos)
        else: return null

else (OUT):
    winner = when:
        courtPos.y < 0                → 1   // P2 overshoot baseline P1 → P1 menang
        courtPos.y > COURT_LENGTH_M   → 2   // P1 overshoot baseline P2 → P2 menang
        else (sideline)               → if lastSide == 1 then 2 else 1
    return PointAwarded(winner, isOut=true, courtPos)
```

**`reset()`:** set `previousVy=0f`, `cooldownFrames=0`, `lastSide=0`, `bounceCountP1=0`, `bounceCountP2=0`. Idempotent.

### `BallTrackingViewModel` (dimodifikasi)

**Constructor tambah `engine: TennisScoreEngine`:**
```kotlin
class BallTrackingViewModel(
    private val engine: TennisScoreEngine
) : ViewModel()
```

Butuh `companion object { fun factory(engine: TennisScoreEngine): ViewModelProvider.Factory }`.

`CameraScreen` menerima `engine` sebagai parameter dari parent (Activity atau NavGraph yang sudah memiliki engine untuk ScoreScreen).

**Tambahan field:**
```kotlin
private val bounceDetector = BounceDetector()
```

**`initDetector()` callback — setelah kalman update:**
```kotlin
val trackedBall = kalmanTracker.update(detections.firstOrNull())
_trackedBall.value = trackedBall

if (trackedBall != null) {
    val mapper = (_calibrationState.value as? CalibrationState.Calibrated)?.mapper
    val courtPos = mapper?.mapToCourtCoords(trackedBall.position)
    val event = bounceDetector.process(trackedBall.velocity.y, courtPos, trackedBall.isPredicted)
    if (event is BounceEvent.PointAwarded) {
        engine.pointWonBy(event.winner)
        kalmanTracker.reset()
        bounceDetector.reset()
        _trackedBall.value = null
    }
}
```

**`onCleared()`:** tidak ada resource tambahan dari Sub-proyek E.

---

## File yang Dibuat / Diubah

| Aksi   | File |
|--------|------|
| Create | `app/src/main/java/com/example/tennisscorer/tracking/BounceEvent.kt` |
| Create | `app/src/main/java/com/example/tennisscorer/tracking/BounceDetector.kt` |
| Modify | `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt` |
| Create | `app/src/test/java/com/example/tennisscorer/tracking/BounceDetectorTest.kt` |
| Modify | `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt` |

---

## Testing

### `BounceDetectorTest` (12 test, JVM pure Kotlin)

1. `process before vy sign change returns null`
2. `isPredicted frame skipped`
3. `first bounce IN P1 half returns null — rally continues`
4. `second bounce IN P1 half returns PointAwarded winner=2`
5. `first bounce IN P2 half returns null — rally continues`
6. `second bounce IN P2 half returns PointAwarded winner=1`
7. `net crossing resets bounce count for new side`
8. `bounce OUT courtPos.y < 0 → winner=1`
9. `bounce OUT courtPos.y > COURT_LENGTH → winner=2`
10. `bounce OUT sideline lastSide=1 → winner=2`
11. `cooldown prevents double detection`
12. `reset clears all state — bounce detectable again`

### `BallTrackingViewModelTest` (+2 test, total 34)

13. `bounce event with calibration calls pointWonBy and resets trackedBall`
14. `bounce event without calibration is ignored`

---

## Threading

| Operasi | Thread |
|---|---|
| `bounceDetector.process()` | camera executor |
| `engine.pointWonBy()` | camera executor (StateFlow write: thread-safe) |
| `bounceDetector.reset()` + `kalmanTracker.reset()` | camera executor (dalam callback, tidak ada race) |

`BounceDetector` tidak thread-safe secara internal — by design, hanya dipanggil dari camera executor thread.

---

## Keterbatasan

- **Net crossing via court Y:** akurasi berkurang saat bola tinggi di udara (HomographyMapper mengasumsikan bola di permukaan court). Dalam praktik untuk kamera elevated, error ini kecil di sekitar net.
- **Tidak handle aturan servis:** seluruh court dianggap valid (tidak cek service box).
- **Single camera:** tidak ada triangulasi 3D — deteksi bergantung pada proyeksi 2D.
- **`isPredicted` frame diabaikan:** frame tanpa deteksi tidak trigger bounce, mengurangi false positive.
- **`lastSide == 0` edge case:** jika bola OUT di sideline sebelum pernah terdeteksi di sisi manapun, default winner=1. Skenario ini sangat jarang (bola harus keluar sebelum frame pertama tracking).

---

## Global Constraints

- `BounceDetector` tidak boleh mengimport package Android/CameraX/OpenCV — pure Kotlin math
- `android.graphics.PointF` diperbolehkan (sama seperti `KalmanTracker`) karena JVM-testable via `isReturnDefaultValues = true`
- Semua konstanta tuning di `BounceDetector.Companion`
- `reset()` idempotent
- YAGNI: tidak ada deteksi servis, tidak ada multi-ball, tidak ada persistensi bounce events ke database
- Tidak ada UI feedback tambahan di CameraScreen — `TennisScoreEngine.scoreState` update otomatis dikonsumsi oleh ScoreScreen
