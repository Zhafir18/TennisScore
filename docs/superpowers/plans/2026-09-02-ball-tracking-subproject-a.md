# Ball Tracking Sub-proyek A: CameraX Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tambahkan live camera preview landscape-mode ke TennisScorer beserta `ImageAnalyzer` pipeline yang siap di-hook oleh Sub-proyek B (YOLOv8 TFLite).

**Architecture:** `BallTrackingViewModel` mengelola state permission dan menyimpan instance `ImageAnalyzer`. `CameraScreen` mengikat CameraX ke lifecycle Composable, menampilkan `PreviewView` penuh layar, dan meneruskan frame ke `ImageAnalyzer`. `FrameAnalyzer` adalah fun interface yang akan diimplementasi oleh Sub-proyek B.

**Tech Stack:** CameraX 1.4.1 (core, camera2, lifecycle, view), Jetpack Compose, Navigation Compose 2.8.5, mockk 1.13.8 (test)

## Global Constraints

- Target SDK 37, min SDK 24
- Landscape only — konsisten dengan seluruh app
- Fully offline — tidak ada network call
- Semua warna gunakan token dari `ui/theme/Color.kt` — tidak ada inline `Color(0x...)` hex literal
- `FrameAnalyzer` adalah satu-satunya kontrak antara Sub-proyek A dan B — jangan ubah signature-nya
- `ImageProxy` harus selalu di-`close()` setelah diproses — tidak boleh ada leak

---

### Task 1: CameraX Dependencies + mockk + Manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: —
- Produces: CameraX tersedia sebagai dependency; `CAMERA` permission ada di manifest; mockk tersedia untuk test

---

- [ ] **Step 1: Tambah versi dan library CameraX + mockk ke libs.versions.toml**

Buka `gradle/libs.versions.toml`. Di block `[versions]`, tambahkan:
```toml
camerax = "1.4.1"
mockk   = "1.13.8"
```

Di block `[libraries]`, tambahkan:
```toml
camerax-core     = { group = "androidx.camera", name = "camera-core",     version.ref = "camerax" }
camerax-camera2  = { group = "androidx.camera", name = "camera-camera2",  version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view     = { group = "androidx.camera", name = "camera-view",     version.ref = "camerax" }
mockk            = { group = "io.mockk",        name = "mockk",           version.ref = "mockk"   }
```

- [ ] **Step 2: Tambah dependencies ke app/build.gradle.kts**

Di block `dependencies { }`, tambahkan:
```kotlin
implementation(libs.camerax.core)
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
implementation(libs.camerax.view)
testImplementation(libs.mockk)
```

- [ ] **Step 3: Tambah CAMERA permission ke AndroidManifest.xml**

Tambahkan dua baris ini sebelum tag `<application>`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

- [ ] **Step 4: Sync Gradle dan verifikasi**

```
./gradlew dependencies --configuration debugRuntimeClasspath
```
Expected: `BUILD SUCCESSFUL`. CameraX dan mockk muncul di dependency tree.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat(tracking): add CameraX 1.4.1 + mockk 1.13.8 deps, add CAMERA permission"
```

---

### Task 2: FrameAnalyzer + ImageAnalyzer + BallTrackingViewModel

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/tracking/FrameAnalyzer.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/ImageAnalyzer.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Test: `app/src/test/java/com/example/tennisscorer/tracking/ImageAnalyzerTest.kt`
- Test: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`

**Interfaces:**
- Consumes: CameraX dependency dari Task 1
- Produces:
  - `fun interface FrameAnalyzer { fun analyze(image: ImageProxy) }` — di package `com.example.tennisscorer.tracking`
  - `class ImageAnalyzer : ImageAnalysis.Analyzer` — dengan `fun setFrameAnalyzer(analyzer: FrameAnalyzer?)` dan `fun analyze(image: ImageProxy)`
  - `class BallTrackingViewModel : ViewModel()` — dengan `val permissionGranted: StateFlow<Boolean>`, `val cameraError: StateFlow<String?>`, `val cameraExecutor: ExecutorService`, `val imageAnalyzer: ImageAnalyzer`, `fun onPermissionResult(granted: Boolean)`, `fun onCameraError(message: String)`, `fun setFrameAnalyzer(analyzer: FrameAnalyzer)`, `fun clearFrameAnalyzer()`

---

- [ ] **Step 1: Tulis failing tests untuk ImageAnalyzer**

Buat `app/src/test/java/com/example/tennisscorer/tracking/ImageAnalyzerTest.kt`:
```kotlin
package com.example.tennisscorer.tracking

import androidx.camera.core.ImageProxy
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAnalyzerTest {

    @Test fun `frame closed when no analyzer set`() {
        val analyzer = ImageAnalyzer()
        val mockProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(mockProxy)
        verify { mockProxy.close() }
    }

    @Test fun `analyzer called when set`() {
        val analyzer = ImageAnalyzer()
        var received: ImageProxy? = null
        analyzer.setFrameAnalyzer { image -> received = image; image.close() }
        val mockProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(mockProxy)
        assertEquals(mockProxy, received)
    }

    @Test fun `frame closed after analyzer cleared`() {
        val analyzer = ImageAnalyzer()
        analyzer.setFrameAnalyzer { image -> image.close() }
        analyzer.setFrameAnalyzer(null)
        val mockProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(mockProxy)
        verify { mockProxy.close() }
    }
}
```

- [ ] **Step 2: Tulis failing tests untuk BallTrackingViewModel**

Buat `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`:
```kotlin
package com.example.tennisscorer.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallTrackingViewModelTest {

    @Test fun `permissionGranted starts false`() {
        val vm = BallTrackingViewModel()
        assertFalse(vm.permissionGranted.value)
    }

    @Test fun `onPermissionResult true sets permissionGranted true`() {
        val vm = BallTrackingViewModel()
        vm.onPermissionResult(true)
        assertTrue(vm.permissionGranted.value)
    }

    @Test fun `onPermissionResult false keeps permissionGranted false`() {
        val vm = BallTrackingViewModel()
        vm.onPermissionResult(true)
        vm.onPermissionResult(false)
        assertFalse(vm.permissionGranted.value)
    }

    @Test fun `cameraError starts null`() {
        val vm = BallTrackingViewModel()
        assertNull(vm.cameraError.value)
    }

    @Test fun `onCameraError sets cameraError message`() {
        val vm = BallTrackingViewModel()
        vm.onCameraError("Kamera tidak dapat dibuka")
        assertEquals("Kamera tidak dapat dibuka", vm.cameraError.value)
    }

    @Test fun `cameraExecutor not null`() {
        val vm = BallTrackingViewModel()
        assertNotNull(vm.cameraExecutor)
    }
}
```

- [ ] **Step 3: Jalankan tests untuk verifikasi FAIL**

```
./gradlew testDebugUnitTest --tests "com.example.tennisscorer.tracking.ImageAnalyzerTest" --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```
Expected: FAIL — class tidak ditemukan.

- [ ] **Step 4: Buat FrameAnalyzer.kt**

Buat `app/src/main/java/com/example/tennisscorer/tracking/FrameAnalyzer.kt`:
```kotlin
package com.example.tennisscorer.tracking

import androidx.camera.core.ImageProxy

fun interface FrameAnalyzer {
    fun analyze(image: ImageProxy)
}
```

- [ ] **Step 5: Buat ImageAnalyzer.kt**

Buat `app/src/main/java/com/example/tennisscorer/tracking/ImageAnalyzer.kt`:
```kotlin
package com.example.tennisscorer.tracking

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class ImageAnalyzer : ImageAnalysis.Analyzer {
    private var frameAnalyzer: FrameAnalyzer? = null

    fun setFrameAnalyzer(analyzer: FrameAnalyzer?) {
        frameAnalyzer = analyzer
    }

    override fun analyze(image: ImageProxy) {
        frameAnalyzer?.analyze(image) ?: image.close()
    }
}
```

- [ ] **Step 6: Buat BallTrackingViewModel.kt**

Buat `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`:
```kotlin
package com.example.tennisscorer.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.tennisscorer.tracking.FrameAnalyzer
import com.example.tennisscorer.tracking.ImageAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BallTrackingViewModel : ViewModel() {

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun onCameraError(message: String) {
        _cameraError.value = message
    }

    fun setFrameAnalyzer(analyzer: FrameAnalyzer) {
        imageAnalyzer.setFrameAnalyzer(analyzer)
    }

    fun clearFrameAnalyzer() {
        imageAnalyzer.setFrameAnalyzer(null)
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
```

- [ ] **Step 7: Jalankan tests untuk verifikasi PASS**

```
./gradlew testDebugUnitTest --tests "com.example.tennisscorer.tracking.ImageAnalyzerTest" --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```
Expected: 9 tests PASS (3 ImageAnalyzerTest + 6 BallTrackingViewModelTest).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/tennisscorer/tracking/ app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt app/src/test/java/com/example/tennisscorer/tracking/ app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
git commit -m "feat(tracking): add FrameAnalyzer, ImageAnalyzer, and BallTrackingViewModel"
```

---

### Task 3: CameraScreen + Navigation + SplashScreen Wiring

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/navigation/Screen.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt`

**Interfaces:**
- Consumes:
  - `BallTrackingViewModel` dari Task 2 — `permissionGranted`, `cameraError`, `cameraExecutor`, `imageAnalyzer`, `onPermissionResult()`, `onCameraError()`
  - `Screen.BallTracking` sealed object
- Produces:
  - `@Composable fun CameraScreen(viewModel: BallTrackingViewModel, onBack: () -> Unit)`
  - `Screen.BallTracking` di `Screen.kt` dengan route `"ball_tracking"`
  - Route `ball_tracking` di `AppNavigation`
  - `SplashScreen(onStart, onHistory, onBallTracking)` — parameter ketiga baru

---

- [ ] **Step 1: Tambah Screen.BallTracking ke Screen.kt**

Buka `app/src/main/java/com/example/tennisscorer/navigation/Screen.kt`. Tambahkan sealed object baru setelah `object Replay`:
```kotlin
object BallTracking : Screen("ball_tracking")
```

File lengkap setelah edit:
```kotlin
package com.example.tennisscorer.navigation

sealed class Screen(val route: String) {
    object Splash      : Screen("splash")
    object Input       : Screen("input")
    object Game        : Screen("game")
    object History     : Screen("history")
    object Replay      : Screen("replay/{matchId}") {
        fun buildRoute(matchId: Long) = "replay/$matchId"
    }
    object BallTracking : Screen("ball_tracking")
}
```

- [ ] **Step 2: Buat CameraScreen.kt**

Buat `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`:
```kotlin
package com.example.tennisscorer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.ui.theme.ActionBtnBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.viewmodels.BallTrackingViewModel

@Composable
fun CameraScreen(
    viewModel: BallTrackingViewModel = viewModel(),
    onBack: () -> Unit
) {
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val cameraError by viewModel.cameraError.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            cameraError != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = cameraError ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
                    ) { Text("Kembali", color = Color.White) }
                }
            }

            !permissionGranted -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Izin kamera diperlukan\nuntuk ball tracking",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) { Text("Beri Izin Kamera", color = Color.White) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) {
                        Text("Kembali", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            else -> {
                val previewView = remember { PreviewView(context) }

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                LaunchedEffect(lifecycleOwner) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also {
                                    it.setAnalyzer(viewModel.cameraExecutor, viewModel.imageAnalyzer)
                                }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            viewModel.onCameraError("Kamera tidak dapat dibuka: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                Text(
                    text = "Camera ready",
                    color = CyanAccent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
                ) {
                    Text("← Kembali", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update AppNavigation.kt — tambah BallTracking route dan update SplashScreen call**

Buka `app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt`.

Tambahkan import berikut di bagian atas (jika belum ada):
```kotlin
import com.example.tennisscorer.ui.screens.CameraScreen
```

Ubah signature `AppNavigation` dan update composable SplashScreen + tambah route baru. Ganti seluruh isi file dengan:
```kotlin
package com.example.tennisscorer.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.screens.CameraScreen
import com.example.tennisscorer.ui.screens.HistoryScreen
import com.example.tennisscorer.ui.screens.PlayerInputScreen
import com.example.tennisscorer.ui.screens.ReplayScreen
import com.example.tennisscorer.ui.screens.ScoreboardScreen
import com.example.tennisscorer.ui.screens.SplashScreen

@Composable
fun AppNavigation(engine: TennisScoreEngine, repository: MatchRepository) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition    = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition     = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition  = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)) }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onStart       = { navController.navigate(Screen.Input.route) },
                onHistory     = { navController.navigate(Screen.History.route) },
                onBallTracking = { navController.navigate(Screen.BallTracking.route) }
            )
        }
        composable(Screen.Input.route) {
            PlayerInputScreen(
                engine       = engine,
                onBack       = { navController.popBackStack() },
                onStartMatch = { navController.navigate(Screen.Game.route) }
            )
        }
        composable(Screen.Game.route) {
            ScoreboardScreen(
                engine        = engine,
                onBackToInput = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                repository   = repository,
                onMatchClick = { matchId -> navController.navigate(Screen.Replay.buildRoute(matchId)) },
                onBack       = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Replay.route,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: return@composable
            ReplayScreen(
                matchId    = matchId,
                repository = repository,
                onBack     = { navController.popBackStack() }
            )
        }
        composable(Screen.BallTracking.route) {
            CameraScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context as? ComponentActivity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}
```

- [ ] **Step 4: Update SplashScreen.kt — tambah parameter onBallTracking dan tombol ketiga**

Buka `app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt`.

Ubah signature fungsi dari:
```kotlin
fun SplashScreen(onStart: () -> Unit, onHistory: () -> Unit) {
```
menjadi:
```kotlin
fun SplashScreen(onStart: () -> Unit, onHistory: () -> Unit, onBallTracking: () -> Unit) {
```

Tambahkan `Spacer` dan `OutlinedButton` baru setelah closing `}` dari OutlinedButton "Riwayat" (sebelum closing `}` dari Column):
```kotlin
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBallTracking,
                modifier = Modifier
                    .width(140.dp)
                    .height(40.dp)
                    .scale(btnScale.value)
                    .graphicsLayer(alpha = btnAlpha.value),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
            ) {
                Text(text = "Ball Tracking", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CyanAccent)
            }
```

- [ ] **Step 5: Verifikasi kompilasi**

```
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`, `app-debug.apk` generated.

- [ ] **Step 6: Manual smoke test di device/emulator**

1. Launch app → SplashScreen menampilkan tiga tombol: "Start", "Riwayat", "Ball Tracking"
2. Tap "Ball Tracking" → permission dialog muncul
3. Tolak permission → layar tampilkan "Izin kamera diperlukan" + tombol "Beri Izin Kamera" + "Kembali"
4. Tap "Kembali" → kembali ke SplashScreen
5. Tap "Ball Tracking" lagi → tap "Beri Izin Kamera" → kamera terbuka, preview penuh layar landscape
6. Text "Camera ready" terlihat di sudut kiri atas
7. Tap "← Kembali" → kembali ke SplashScreen
8. Navigasi ke layar lain (Start → Input, Riwayat → History) masih berfungsi normal

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt app/src/main/java/com/example/tennisscorer/navigation/Screen.kt app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt
git commit -m "feat(tracking): add CameraScreen, BallTracking route, and Ball Tracking button on SplashScreen"
```
