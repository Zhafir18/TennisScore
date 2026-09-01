# Design Spec: Animation Layer

**Date:** 2026-09-01
**Status:** Approved
**Scope:** Tambah animasi pada seluruh app TennisScorer untuk meningkatkan UX

---

## Tujuan

Menambahkan animasi bermakna di momen-momen kunci agar app terasa polished dan responsif, tanpa mengganggu keterbacaan skor saat pertandingan berlangsung. Tidak ada dependency baru — semua menggunakan Compose Animation API bawaan.

---

## Global Constraints

- Tidak ada library animasi pihak ketiga — hanya `androidx.compose.animation.*`
- Semua animasi menggunakan Compose Animation API yang secara otomatis menghormati setting sistem "Animator duration scale = 0" (Android Developer Options) — tidak perlu handling manual
- Durasi animasi tidak boleh melebihi 600ms agar tidak memperlambat alur permainan
- Tidak ada perubahan pada logika scoring (`TennisScoreEngine`) atau struktur navigasi

---

## Perubahan Per File

### `ui/screens/SplashScreen.kt` — Entrance Staggered + Bouncing Ball

**Entrance staggered:**
- Gunakan 4 `Animatable<Float>` untuk alpha/scale tiap elemen
- `LaunchedEffect(Unit)` memicu semua secara berurutan dengan `delay()`

| Elemen | Animasi | Delay |
|---|---|---|
| `TennisAppIcon` | scale `0.5→1.0` + alpha `0→1`, spring `DampingRatioMediumBouncy` | 0ms |
| "Tennis Score" | translateY `20dp→0` + alpha `0→1`, tween 300ms | 150ms |
| "Smart Court Scoreboard" | translateY `20dp→0` + alpha `0→1`, tween 300ms | 280ms |
| Tombol "Start" | scale `0.8→1.0` + alpha `0→1`, tween 250ms | 420ms |

**Bouncing ball:**
- `rememberInfiniteTransition()` untuk animasi infinite
- `animateFloat` dengan `keyframes` untuk posisi Y:
  - 0ms: Y = 0 (atas lompatan)
  - 400ms: Y = maxY (bawah, menyentuh tanah) — `FastOutSlowIn` easing
  - 800ms: Y = 0 (kembali ke atas) — `LinearOutSlowIn` easing
- Efek squish di titik bawah: `animateFloat` untuk scaleX (`1.0→1.3→1.0`) dan scaleY (`1.0→0.7→1.0`) sinkron dengan keyframe posisi Y
- Bola ditampilkan sebagai `Text("🎾", fontSize = 28.sp)` dalam `Box(Modifier.fillMaxSize())` dengan `align(Alignment.BottomStart)` + `offset(x = 48.dp)` dan Y offset dari nilai animasi
- Range Y: dari `0.dp` (posisi puncak lompatan, 80dp di atas batas bawah layar) hingga `80.dp` (menyentuh batas bawah) — implementasi: `offset(y = animatedY - 80.dp)` relatif terhadap bottom alignment
- Bola diletakkan sebagai elemen pertama di dalam `Box` utama sehingga selalu di bawah konten UI (z-order Compose = urutan deklarasi)

### `navigation/AppNavigation.kt` — Transisi Antar Screen

Tambahkan `enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition` pada setiap `composable()` di `NavHost`:

```
Maju (push):
  enter  : slideInHorizontally { it } + fadeIn(tween(300))
  exit   : slideOutHorizontally { -it/3 } + fadeOut(tween(300))

Mundur (pop):
  popEnter  : slideInHorizontally { -it/3 } + fadeIn(tween(300))
  popExit   : slideOutHorizontally { it } + fadeOut(tween(300))
```

Pola `{ -it/3 }` untuk layar yang "ditinggalkan" memberi kesan depth (layar lama bergerak lebih lambat dari layar baru).

### `ui/screens/ScoreboardScreen.kt` — Score Animation + Flash

**Score animation:**
- Ganti `Text(leftScore)` dan `Text(rightScore)` dengan `AnimatedContent(targetState = leftScore / rightScore)`
- `ContentTransform`:
  - `slideInVertically { -it } + fadeIn` (masuk dari atas)
  - `slideOutVertically { it } + fadeOut` (keluar ke bawah)
- `SizeTransform(clip = false)` agar angka besar tidak ter-clip saat transisi

**Flash feedback:**
- Tambahkan state `var flashLeft by remember { mutableStateOf(false) }` dan `flashRight`
- Saat `engine.pointWonBy(leftPlayerId)` dipanggil, set `flashLeft = true`
- `LaunchedEffect(flashLeft)` → delay 200ms → set kembali false
- Overlay `Box` semi-transparan putih dengan `animateFloatAsState` untuk alpha (`0.25f` saat flash, `0f` saat tidak) di atas panel yang mencetak poin

### `ui/components/WinnerOverlay.kt` — Dramatic Entrance + Trophy Pulse

**Card entrance:**
- Bungkus seluruh `Card` dengan `AnimatedVisibility(visible = true)`:
  - `enter = scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(300))`
- Backdrop: `Box` background `Color.Black.copy(alpha)` dengan `animateFloatAsState` dari `0f → 0.65f` (tween 400ms)

**Trophy pulse:**
- `rememberInfiniteTransition()` untuk emoji 🏆
- `animateFloat` scale `1.0→1.12→1.0`, `tween(800ms, easing = FastOutSlowIn)`, infinite

### `ui/screens/PlayerInputScreen.kt` — PlayerCard Slide-in

- Tambahkan state `var visible by remember { mutableStateOf(false) }`
- `LaunchedEffect(Unit)` → set `visible = true` setelah delay kecil (50ms) agar rekomposisi screen selesai dulu
- Bungkus card Player 1 dengan `AnimatedVisibility(visible, enter = slideInHorizontally { -it } + fadeIn)`
- Bungkus card Player 2 dengan `AnimatedVisibility(visible, enter = slideInHorizontally { it } + fadeIn)` + `delay(100ms)` extra agar keduanya tidak muncul bersamaan

---

## Yang TIDAK Berubah

- `TennisScoreEngine.kt` — tidak ada perubahan logika
- `TennisScoreState.kt` — tidak ada perubahan
- `ui/theme/Color.kt` — tidak ada token baru (animasi tidak memerlukan warna baru)
- `TennisAppIcon.kt`, `PlayerCard.kt`, `ScoreBadge.kt` — tidak diubah
- Logika changeover, tiebreak, reset — tidak diubah

---

## Urutan Implementasi

1. Splash entrance staggered + bouncing ball (`SplashScreen.kt`)
2. Navigation transitions (`AppNavigation.kt`)
3. Score `AnimatedContent` + flash overlay (`ScoreboardScreen.kt`)
4. WinnerOverlay dramatic entrance + trophy pulse (`WinnerOverlay.kt`)
5. PlayerCard slide-in (`PlayerInputScreen.kt`)
