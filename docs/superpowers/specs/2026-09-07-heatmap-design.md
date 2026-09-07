# Ball Placement Heatmap

**Tanggal:** 2026-09-07
**Status:** Draft

---

## Tujuan

Menampilkan heatmap posisi bounce bola berbasis density (KDE) di dua tempat:

1. **Live** — mini-map di pojok kanan bawah `CameraScreen` saat pertandingan berlangsung
2. **History** — tampilan full-size di `ReplayScreen` setelah pertandingan selesai

Heatmap menggabungkan semua bounce dalam satu match (tidak difilter per set/game).

---

## Arsitektur

```
BounceEvent.PointAwarded (camera executor thread)
  → BounceRepository.insert(BounceRecord)        [IO dispatcher]
  → _bouncePoints update (StateFlow)
      → HeatmapRenderer.render(points) → Bitmap  [Default dispatcher]
          → CameraScreen mini-map (live)

matchId (History)
  → BounceRepository.getByMatchId(matchId)
  → HeatmapRenderer.render(points) → Bitmap
      → ReplayScreen heatmap tab (full-size)
```

---

## Data Layer

### `BounceRecord` (Room entity baru)

```kotlin
@Entity(tableName = "bounce_records")
data class BounceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val x: Float,       // koordinat court, 0–10.97m
    val y: Float,       // koordinat court, 0–23.77m
    val player: Int     // 1 atau 2 (pemenang poin dari bounce ini)
)
```

### `BounceDao`

```kotlin
@Dao
interface BounceDao {
    @Insert
    suspend fun insert(record: BounceRecord)

    @Query("SELECT * FROM bounce_records WHERE matchId = :matchId")
    suspend fun getByMatchId(matchId: Long): List<BounceRecord>
}
```

### `BounceRepository`

Wrapper DAO tipis — mengikuti pola `MatchRepository`:

```kotlin
class BounceRepository(private val dao: BounceDao) {
    suspend fun insert(record: BounceRecord) = dao.insert(record)
    suspend fun getByMatchId(matchId: Long) = dao.getByMatchId(matchId)
}
```

### `TennisScorerDatabase`

Tambahkan `BounceRecord` ke `entities`, `BounceDao` sebagai abstract function, bump `version` ke 3, sertakan `Migration(2, 3)` yang menjalankan `CREATE TABLE bounce_records (...)`.

---

## Rendering Layer

### `HeatmapRenderer` (pure Kotlin + Android Bitmap, tidak ada dependency UI)

**Konstanta:**
```kotlin
const val BITMAP_W = 220        // piksel lebar output
const val BITMAP_H = 476        // piksel tinggi output (proporsional 10.97 × 23.77)
const val SIGMA_PX = 30f        // radius Gaussian dalam piksel (~1.5m)
```

**Fungsi utama:**
```kotlin
fun render(points: List<PointF>): Bitmap
```

**Langkah-langkah:**
1. Buat `FloatArray(BITMAP_W × BITMAP_H)` bernilai 0
2. Untuk setiap titik, konversi koordinat court (meter) ke piksel:
   - `px = x / COURT_WIDTH_M * BITMAP_W`
   - `py = y / COURT_LENGTH_M * BITMAP_H`
3. Tambahkan Gaussian kernel (`e^(-d² / 2σ²)`) ke semua piksel dalam radius `3σ`
4. Normalize ke 0–1 (bagi dengan nilai maksimum; jika semua nol, kembalikan bitmap transparan)
5. Map nilai ke warna ARGB dengan gradient: `0.0 → transparan biru → cyan → hijau → kuning → merah → 1.0`
6. Tulis ke `Bitmap.Config.ARGB_8888` dan kembalikan

**`render()` bersifat pure** — tidak ada state internal, tidak ada side effect. Aman dipanggil dari coroutine manapun.

### `CourtHeatmapView` (Composable shared)

```kotlin
@Composable
fun CourtHeatmapView(
    heatmapBitmap: Bitmap?,
    bounceCount: Int,
    modifier: Modifier = Modifier
)
```

- Background hitam
- Canvas menggambar garis lapangan putih tipis (baseline, net, kedua sideline, service line) sesuai proporsi `COURT_WIDTH_M × COURT_LENGTH_M`
- Bitmap heatmap di-overlay di atas garis dengan alpha 0.75
- Label `"$bounceCount bounces"` di pojok kiri bawah (warna abu-abu, font kecil)
- Jika `heatmapBitmap == null`: tampilkan hanya garis lapangan (belum ada data)

Composable ini dipakai di live camera dan history tanpa modifikasi.

---

## ViewModel Layer

### `TennisScoreEngine` (modifikasi kecil)

Tambah `SharedFlow` yang emit `matchId` saat match selesai disimpan ke DB:

```kotlin
private val _matchSavedEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
val matchSavedEvent: SharedFlow<Long> = _matchSavedEvent.asSharedFlow()
```

Di `persistMatch()`, setelah `repository.saveMatch(...)` berhasil, emit matchId yang dikembalikan Room:

```kotlin
val savedId = repository.saveMatch(matchRecord, snapshot)  // returns Long
_matchSavedEvent.emit(savedId)
```

`MatchRepository.saveMatch()` diperbarui agar mengembalikan `Long` (ID yang di-generate Room).

### `BallTrackingViewModel` (dimodifikasi)

Tambah dependency `BounceRepository` (tanpa `matchId` di constructor — matchId belum diketahui saat camera dibuka):

```kotlin
class BallTrackingViewModel(
    private val engine: TennisScoreEngine,
    private val bounceRepo: BounceRepository
) : ViewModel()
```

Tambah state:

```kotlin
private val _heatmapBitmap = MutableStateFlow<Bitmap?>(null)
val heatmapBitmap: StateFlow<Bitmap?> = _heatmapBitmap.asStateFlow()

private val _bounceCount = MutableStateFlow(0)
val bounceCount: StateFlow<Int> = _bounceCount.asStateFlow()

private val _bouncePoints = mutableListOf<PointF>()
private val _pendingBounces = mutableListOf<BounceRecord>()
```

Di `init {}`, collect `engine.matchSavedEvent` — saat match selesai, simpan semua bounce yang terkumpul ke DB:

```kotlin
init {
    viewModelScope.launch {
        engine.matchSavedEvent.collect { matchId ->
            val toSave = _pendingBounces.map { it.copy(matchId = matchId) }
            launch(Dispatchers.IO) { toSave.forEach { bounceRepo.insert(it) } }
            _pendingBounces.clear()
        }
    }
}
```

Modifikasi `handleBounceEvent` — simpan bounce ke pending list (in-memory) dan update heatmap live:

```kotlin
internal fun handleBounceEvent(event: BounceEvent) {
    if (event is BounceEvent.PointAwarded) {
        engine.pointWonBy(event.winner)

        _bouncePoints.add(event.courtPos)
        _pendingBounces.add(BounceRecord(matchId = 0, x = event.courtPos.x,
                                         y = event.courtPos.y, player = event.winner))
        viewModelScope.launch(Dispatchers.Default) {
            _heatmapBitmap.value = HeatmapRenderer.render(_bouncePoints.toList())
            _bounceCount.value = _bouncePoints.size
        }

        kalmanTracker.reset()
        bounceDetector.reset()
        _trackedBall.value = null
    }
}
```

Factory diperbarui untuk menerima `BounceRepository`.

### `HeatmapViewModel` (baru, untuk history)

```kotlin
class HeatmapViewModel(
    private val matchId: Long,
    private val bounceRepo: BounceRepository
) : ViewModel() {

    val heatmapBitmap: StateFlow<Bitmap?> = ...
    val bounceCount: StateFlow<Int> = ...

    init {
        viewModelScope.launch {
            val records = bounceRepo.getByMatchId(matchId)
            val points = records.map { PointF(it.x, it.y) }
            _bounceCount.value = points.size
            _heatmapBitmap.value = if (points.isEmpty()) null
                                   else HeatmapRenderer.render(points)
        }
    }
}
```

---

## UI Layer

### CameraScreen — mini-map live

Tambah `Box` di pojok kanan bawah (di atas tombol Kembali), ukuran `120×260dp`, background `Color.Black.copy(alpha = 0.6f)`, border tipis warna CyanAccent:

```kotlin
val heatmapBitmap by viewModel.heatmapBitmap.collectAsState()
val bounceCount by viewModel.bounceCount.collectAsState()

Box(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 72.dp)
        .size(width = 120.dp, height = 260.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color.Black.copy(alpha = 0.6f))
        .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
) {
    CourtHeatmapView(heatmapBitmap, bounceCount, Modifier.fillMaxSize())
}
```

### ReplayScreen — tab Heatmap

Tambah `TabRow` dengan dua tab: **Replay** dan **Heatmap**. Saat tab Heatmap aktif, tampilkan `CourtHeatmapView` full-size (mengisi lebar layar, height proporsional) dengan `HeatmapViewModel`.

---

## File yang Dibuat / Diubah

| Aksi   | File |
|--------|------|
| Create | `data/BounceRecord.kt` |
| Create | `data/BounceDao.kt` |
| Create | `data/BounceRepository.kt` |
| Modify | `data/TennisScorerDatabase.kt` — tambah entity, dao, migration v2→v3 |
| Create | `tracking/HeatmapRenderer.kt` |
| Create | `ui/components/CourtHeatmapView.kt` |
| Create | `ui/viewmodels/HeatmapViewModel.kt` |
| Modify | `ui/viewmodels/BallTrackingViewModel.kt` — tambah repo, heatmap state, pending bounces |
| Modify | `ui/screens/CameraScreen.kt` — tambah mini-map |
| Modify | `ui/screens/ReplayScreen.kt` — tambah tab Heatmap |
| Modify | `TennisScoreEngine.kt` — tambah matchSavedEvent SharedFlow |
| Modify | `data/MatchRepository.kt` — saveMatch() returns Long |
| Modify | `MainActivity.kt` — inject BounceRepository ke navigation |
| Modify | `navigation/AppNavigation.kt` — pass BounceRepository ke CameraScreen |

---

## Testing

### `HeatmapRendererTest` (JVM pure, tidak butuh emulator)

1. `render empty list returns null bitmap` — (atau bitmap transparan sepenuhnya)
2. `render single point creates non-null bitmap`
3. `render returns bitmap of correct dimensions`
4. `render multiple points — center pixel brighter than edge`
5. `render is deterministic — same input same output`

### `BounceRepositoryTest`

6. `insert and getByMatchId returns inserted records`
7. `getByMatchId filters by matchId correctly`

---

## Threading

| Operasi | Thread |
|---|---|
| `bounceRepo.insert()` | IO dispatcher (viewModelScope) |
| `HeatmapRenderer.render()` | Default dispatcher (viewModelScope) |
| `_heatmapBitmap.value = ...` | Default dispatcher → StateFlow thread-safe |
| `CourtHeatmapView` recompose | Main thread (Compose) |

---

## Keterbatasan

- Heatmap tidak difilter per set/game — seluruh match digabung
- `_bouncePoints` dan `_pendingBounces` adalah in-memory; jika app dikill sebelum match selesai, live heatmap dan bounce records hilang (belum ada matchId untuk disimpan ke DB)
- Jika kamera dibuka tanpa match aktif (dari SplashScreen langsung), bounce points terkumpul tapi tidak pernah disimpan ke DB (tidak ada `matchSavedEvent` yang emit)
- `HeatmapRenderer.render()` adalah O(points × BITMAP_W × BITMAP_H) — untuk match normal (<200 bounce), ini cukup cepat di Default dispatcher
- Tidak ada heatmap per pemain (P1 vs P2 dipisah warna) — semua bounce satu warna density

---

## Constraints

- `HeatmapRenderer` tidak boleh import package Android UI (Activity, View, Composable) — hanya `android.graphics.Bitmap`, `android.graphics.PointF`
- `CourtHeatmapView` tidak boleh memanggil `HeatmapRenderer` langsung — hanya menerima `Bitmap?` sebagai parameter
- Database migration wajib ada — tidak boleh `fallbackToDestructiveMigration`
- YAGNI: tidak ada filter per set/game, tidak ada export/share heatmap, tidak ada heatmap per pemain
