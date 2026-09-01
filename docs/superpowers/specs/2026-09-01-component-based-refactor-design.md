# Design Spec: Component-Based Refactor + Navigation Compose

**Date:** 2026-09-01  
**Status:** Approved  
**Scope:** Refactor arsitektur TennisScorer menjadi component-based dengan Navigation Compose

---

## Tujuan

Memecah `MainActivity.kt` monolitik menjadi struktur component-based yang proper:
- Tiap screen punya file sendiri
- Komponen UI reusable dipisah ke folder `components/`
- Navigasi menggunakan `NavController` (Navigation Compose) menggantikan `currentScreen` string
- State nama pemain dipindah ke ViewModel

---

## Struktur File Target

```
app/src/main/java/com/example/tennisscorer/
├── MainActivity.kt                  ← hanya setContent + viewModel
├── navigation/
│   ├── Screen.kt                    ← sealed class route definitions
│   └── AppNavigation.kt             ← NavHost + composable routes
├── data/
│   ├── TennisScoreEngine.kt         ← ViewModel (tambah name input state)
│   └── TennisScoreState.kt          ← tidak berubah
└── ui/
    ├── theme/
    │   ├── Color.kt                 ← semua design tokens (AppBg, CardBg, dll)
    │   ├── Theme.kt                 ← tidak berubah
    │   └── Type.kt                  ← tidak berubah
    ├── screens/
    │   ├── SplashScreen.kt
    │   ├── PlayerInputScreen.kt
    │   └── ScoreboardScreen.kt
    └── components/
        ├── TennisAppIcon.kt
        ├── PlayerCard.kt
        ├── ScoreBadge.kt
        └── WinnerOverlay.kt
```

---

## Perubahan Per File

### `navigation/Screen.kt`
Sealed class mendefinisikan semua route:
```kotlin
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Input  : Screen("input")
    object Game   : Screen("game")
}
```

### `navigation/AppNavigation.kt`
`NavHost` dengan 3 destination. Juga memanggil `LockScreenOrientation` di sini:
```kotlin
@Composable
fun AppNavigation(engine: TennisScoreEngine) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) { SplashScreen(onStart = { navController.navigate(Screen.Input.route) }) }
        composable(Screen.Input.route)  { PlayerInputScreen(engine, onBack = { navController.popBackStack() }, onStartMatch = { navController.navigate(Screen.Game.route) }) }
        composable(Screen.Game.route)   { ScoreboardScreen(engine, onBackToInput = { navController.popBackStack() }) }
    }
}
```

### `MainActivity.kt`
Hanya `setContent` + viewModel, tidak ada composable visual:
```kotlin
class MainActivity : ComponentActivity() {
    private val engine: TennisScoreEngine by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TennisScorerTheme { AppNavigation(engine) } }
    }
}
```

### `data/TennisScoreEngine.kt`
Tambah dua field state untuk input nama pemain (supaya survive navigation):
```kotlin
var p1NameInput by mutableStateOf("")
    private set
var p2NameInput by mutableStateOf("")
    private set

fun updateP1Name(name: String) { p1NameInput = name }
fun updateP2Name(name: String) { p2NameInput = name }
```
Field `p1NameInput`/`p2NameInput` yang sebelumnya ada di `TennisApp` sebagai `rememberSaveable` dihapus.

### `ui/theme/Color.kt`
Pindahkan semua design tokens dari `MainActivity.kt`:
```kotlin
val AppBg      = Color(0xFF050A1A)
val CardBg     = Color(0xFF0F1629)
val CyanAccent = Color(0xFF00B8D9)
val RedAccent  = Color(0xFFDC2626)
val BlueAccent = Color(0xFF818CF8)
val ScoreRed   = Color(0xFF6B0F0F)
val ScoreBlue  = Color(0xFF1B1B52)
val VsGreen    = Color(0xFFB5FF00)
val Gold       = Color(0xFFFFD700)
```

### `ui/screens/SplashScreen.kt`
Ekstrak `SplashScreen` composable dari `MainActivity.kt`. Import tokens dari `ui/theme/Color.kt`.

### `ui/screens/PlayerInputScreen.kt`
Ekstrak `PlayerInputScreen`. Terima `engine: TennisScoreEngine` langsung (ambil name input dari engine).

### `ui/screens/ScoreboardScreen.kt`
Ekstrak `LandscapeTennisScoreboard`, rename menjadi `ScoreboardScreen`. Terima `engine` langsung.

### `ui/components/TennisAppIcon.kt`
Ekstrak `TennisAppIcon` composable.

### `ui/components/PlayerCard.kt`
Ekstrak `PlayerCard` composable.

### `ui/components/ScoreBadge.kt`
Ekstrak badge Match/Game menjadi komponen sendiri dengan parameter:
```kotlin
@Composable
fun ScoreBadge(p1Sets: Int, p2Sets: Int, p1Games: Int, p2Games: Int)
```

### `ui/components/WinnerOverlay.kt`
Ekstrak overlay pemenang menjadi komponen sendiri:
```kotlin
@Composable
fun WinnerOverlay(winnerName: String, onPlayAgain: () -> Unit)
```

### `LockScreenOrientation`
Pindah ke `AppNavigation.kt` (tidak perlu file sendiri).

---

## Dependency Tambahan

Tambah ke `app/build.gradle.kts`:
```kotlin
implementation(libs.androidx.navigation.compose)
```

Tambah ke `gradle/libs.versions.toml`:
```toml
[versions]
navigationCompose = "2.8.5"   # Navigation tidak di-manage BOM, versi eksplisit diperlukan

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

---

## Yang TIDAK Berubah

- `TennisScoreState.kt` — tidak ada perubahan
- Logika scoring di `TennisScoreEngine.pointWonBy()` — tidak ada perubahan
- `resetMatch()` dan `resetScore()` — tidak ada perubahan
- Tampilan visual semua screen — tidak ada perubahan
- `ui/theme/Theme.kt` dan `Type.kt` — tidak ada perubahan

---

## Alur Navigasi

```
Splash ──onStart──▶ Input ──onStartMatch──▶ Game
                    ◀──popBackStack──      ◀──navigate(Input) + popUpTo──
```

Back button di Input → `popBackStack()` kembali ke Splash.  
"Change Player" di Game → `popBackStack()` kembali ke Input (stack: Splash → Input → Game, pop Game → Input).

---

## Urutan Implementasi

1. Tambah dependency Navigation Compose di gradle
2. Buat `navigation/Screen.kt`
3. Pindah design tokens ke `ui/theme/Color.kt`
4. Tambah `p1NameInput`, `p2NameInput`, `updateP1Name`, `updateP2Name` ke ViewModel
5. Buat `ui/components/` — TennisAppIcon, PlayerCard, ScoreBadge, WinnerOverlay
6. Buat `ui/screens/` — SplashScreen, PlayerInputScreen, ScoreboardScreen
7. Buat `navigation/AppNavigation.kt`
8. Simplify `MainActivity.kt`
9. Hapus kode lama dari `MainActivity.kt`
10. Verify build
