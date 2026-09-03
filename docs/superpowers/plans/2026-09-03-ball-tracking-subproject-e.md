# Ball Tracking Sub-proyek E — Bounce Detection + In/Out + Auto-Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounce detection to the ball tracking pipeline — detect bounce location (IN/OUT, which side of court), apply second-bounce scoring rules, and auto-call `TennisScoreEngine.pointWonBy()`.

**Architecture:** `BounceDetector` is a pure Kotlin class (like `KalmanTracker`) that tracks rally state — per-side bounce counts that reset on net crossing — and emits `BounceEvent.PointAwarded` on a second IN bounce or any OUT bounce. `BallTrackingViewModel` gains a `TennisScoreEngine` constructor parameter and calls `BounceDetector` per frame after mapping ball position to court coordinates via `HomographyMapper`.

**Tech Stack:** Kotlin, AndroidX ViewModel + ViewModelProvider.Factory, StateFlow, android.graphics.PointF (JVM-testable via `isReturnDefaultValues = true`), MockK

## Global Constraints

- `BounceDetector` must not import Android/CameraX/OpenCV packages — pure Kotlin math; `android.graphics.PointF` is permitted (JVM-testable via `testOptions { unitTests { isReturnDefaultValues = true } }` already in `app/build.gradle.kts`)
- All tuning constants in `BounceDetector.Companion`: `BOUNCE_COOLDOWN_FRAMES = 10`, `VELOCITY_THRESHOLD = 0.005f`, `NET_Y_M = HomographyMapper.COURT_LENGTH_M / 2f`
- Court in-bounds: `x ∈ [0f, HomographyMapper.COURT_WIDTH_M]` and `y ∈ [0f, HomographyMapper.COURT_LENGTH_M]`
- Player sides: `y < NET_Y_M` = P1's half; `y ≥ NET_Y_M` = P2's half; `y = 0` = P1's baseline; `y = COURT_LENGTH_M` = P2's baseline
- `reset()` must be idempotent (safe to call multiple times)
- YAGNI: no serve detection, no service box validation, no multi-ball tracking, no bounce event DB persistence
- No UI feedback added to CameraScreen

---

### Task 1: BounceEvent + BounceDetector (TDD)

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/tracking/BounceEvent.kt`
- Create: `app/src/main/java/com/example/tennisscorer/tracking/BounceDetector.kt`
- Create: `app/src/test/java/com/example/tennisscorer/tracking/BounceDetectorTest.kt`

**Interfaces:**
- Produces: `BounceDetector.process(vy: Float, courtPos: PointF?, isPredicted: Boolean): BounceEvent?`
- Produces: `BounceDetector.reset(): Unit`
- Produces: `BounceEvent.PointAwarded(winner: Int, isOut: Boolean, courtPos: PointF)` — Task 2 consumes this

---

- [ ] **Step 1: Create BounceEvent.kt**

```kotlin
// app/src/main/java/com/example/tennisscorer/tracking/BounceEvent.kt
package com.example.tennisscorer.tracking

import android.graphics.PointF

sealed class BounceEvent {
    data class PointAwarded(
        val winner: Int,
        val isOut: Boolean,
        val courtPos: PointF
    ) : BounceEvent()
}
```

- [ ] **Step 2: Write BounceDetectorTest.kt — all 12 tests (they will FAIL until Step 3)**

```kotlin
// app/src/test/java/com/example/tennisscorer/tracking/BounceDetectorTest.kt
package com.example.tennisscorer.tracking

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BounceDetectorTest {

    private lateinit var detector: BounceDetector

    private val W   = HomographyMapper.COURT_WIDTH_M    // 10.97f
    private val L   = HomographyMapper.COURT_LENGTH_M   // 23.77f
    private val NET = BounceDetector.NET_Y_M            // 11.885f
    private val VY  = BounceDetector.VELOCITY_THRESHOLD + 0.01f  // just above threshold

    private fun pos(x: Float, y: Float) = PointF().also { it.x = x; it.y = y }

    @Before fun setUp() { detector = BounceDetector() }

    @Test fun `process before vy sign change returns null`() {
        val p = pos(W / 2f, NET / 2f)
        assertNull(detector.process(VY, p, false))
        assertNull(detector.process(VY, p, false))
    }

    @Test fun `isPredicted frame skipped — no bounce triggered`() {
        val p = pos(W / 2f, NET / 2f)
        detector.process(VY, p, false)         // prime previousVy = VY
        // isPredicted=true: should NOT detect bounce even with sign change
        assertNull(detector.process(-VY, p, true))
    }

    @Test fun `first bounce IN P1 half returns null — rally continues`() {
        val p = pos(W / 2f, NET / 2f - 1f)   // y < NET → P1 half
        detector.process(VY, p, false)
        assertNull(detector.process(-VY, p, false))
    }

    @Test fun `second bounce IN P1 half returns PointAwarded winner=2`() {
        val p = pos(W / 2f, NET / 2f - 1f)   // P1 half
        // First bounce → null
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        // Drain cooldown (10 frames, each with VY so previousVy=VY when cooldown ends)
        repeat(BounceDetector.BOUNCE_COOLDOWN_FRAMES) { detector.process(VY, p, false) }
        // Second bounce
        detector.process(VY, p, false)        // re-prime after drain
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(2, result.winner)
        assertFalse(result.isOut)
    }

    @Test fun `first bounce IN P2 half returns null — rally continues`() {
        val p = pos(W / 2f, NET + 1f)        // y > NET → P2 half
        detector.process(VY, p, false)
        assertNull(detector.process(-VY, p, false))
    }

    @Test fun `second bounce IN P2 half returns PointAwarded winner=1`() {
        val p = pos(W / 2f, NET + 1f)        // P2 half
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        repeat(BounceDetector.BOUNCE_COOLDOWN_FRAMES) { detector.process(VY, p, false) }
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(1, result.winner)
        assertFalse(result.isOut)
    }

    @Test fun `net crossing resets bounce count — first bounce after crossing returns null`() {
        val posP1 = pos(W / 2f, NET / 2f - 1f)  // P1 half
        val posP2 = pos(W / 2f, NET + 1f)        // P2 half
        // First bounce on P1 side
        detector.process(VY, posP1, false)
        detector.process(-VY, posP1, false)
        repeat(BounceDetector.BOUNCE_COOLDOWN_FRAMES) { detector.process(VY, posP1, false) }
        // Ball crosses to P2 side (resets bounceCountP2) then back to P1 (resets bounceCountP1)
        detector.process(VY, posP2, false)
        detector.process(VY, posP1, false)
        // Re-prime and bounce on P1 — count was reset, so this is first → null
        detector.process(VY, posP1, false)
        val result = detector.process(-VY, posP1, false)
        assertNull(result)
    }

    @Test fun `bounce OUT courtPos y below 0 returns winner=1`() {
        // Ball past P1 baseline (y < 0) → P2 overshot → P1 wins
        val p = pos(W / 2f, -1f)
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(1, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `bounce OUT courtPos y above COURT_LENGTH returns winner=2`() {
        // Ball past P2 baseline (y > L) → P1 overshot → P2 wins
        val p = pos(W / 2f, L + 1f)
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(2, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `bounce OUT sideline with lastSide=1 returns winner=2`() {
        // Set lastSide=1 via P1-half tracking frame
        detector.process(0f, pos(W / 2f, NET / 2f - 1f), false)
        // Bounce OUT at sideline (x < 0) still in P1 half y
        val outPos = pos(-1f, NET / 2f - 1f)
        detector.process(VY, outPos, false)
        val result = detector.process(-VY, outPos, false)
        assertNotNull(result)
        result as BounceEvent.PointAwarded
        assertEquals(2, result.winner)
        assertTrue(result.isOut)
    }

    @Test fun `cooldown prevents double detection`() {
        val p = pos(W / 2f, NET + 1f)   // P2 half
        // First bounce (bounceCountP2 = 1)
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        // Immediately try second bounce (cooldown = 10, not drained)
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNull(result)  // still in cooldown → null
    }

    @Test fun `reset clears all state — first bounce after reset returns null`() {
        val p = pos(W / 2f, NET + 1f)   // P2 half
        // First bounce (bounceCountP2 = 1, cooldown = 10)
        detector.process(VY, p, false)
        detector.process(-VY, p, false)
        // Reset clears bounce count and cooldown
        detector.reset()
        // After reset: fresh start — next two calls should behave as first-ever bounce → null
        detector.process(VY, p, false)
        val result = detector.process(-VY, p, false)
        assertNull(result)  // bounceCountP2 was reset to 0, now = 1 < 2 → null
    }
}
```

- [ ] **Step 3: Run the tests — verify they FAIL**

```
./gradlew testDebugUnitTest --tests "com.example.tennisscorer.tracking.BounceDetectorTest"
```

Expected: BUILD SUCCESSFUL but tests FAIL with `ClassNotFoundException` or `NoClassDefFoundError` (BounceDetector not created yet).

- [ ] **Step 4: Create BounceDetector.kt**

```kotlin
// app/src/main/java/com/example/tennisscorer/tracking/BounceDetector.kt
package com.example.tennisscorer.tracking

import android.graphics.PointF

class BounceDetector {

    companion object {
        const val BOUNCE_COOLDOWN_FRAMES = 10
        const val VELOCITY_THRESHOLD = 0.005f
        val NET_Y_M = HomographyMapper.COURT_LENGTH_M / 2f  // 11.885f
    }

    private var previousVy     = 0f
    private var cooldownFrames = 0
    private var lastSide       = 0   // 0=unknown, 1=P1 half (y<NET), 2=P2 half (y≥NET)
    private var bounceCountP1  = 0
    private var bounceCountP2  = 0

    fun process(vy: Float, courtPos: PointF?, isPredicted: Boolean): BounceEvent? {
        // Step 1: net crossing detection — runs every frame regardless of cooldown
        if (courtPos != null) {
            val currentSide = if (courtPos.y < NET_Y_M) 1 else 2
            if (lastSide != 0 && currentSide != lastSide) {
                if (currentSide == 1) bounceCountP1 = 0 else bounceCountP2 = 0
            }
            lastSide = currentSide
        }

        // Step 2: cooldown & predicted guard
        if (isPredicted || cooldownFrames > 0) {
            cooldownFrames = maxOf(0, cooldownFrames - 1)
            previousVy = vy
            return null
        }

        // Step 3: bounce detection — vy sign change from positive to negative
        val bounce = previousVy > VELOCITY_THRESHOLD && vy < -VELOCITY_THRESHOLD
        previousVy = vy
        if (!bounce || courtPos == null) return null

        cooldownFrames = BOUNCE_COOLDOWN_FRAMES

        val isIn = courtPos.x >= 0f && courtPos.x <= HomographyMapper.COURT_WIDTH_M &&
                   courtPos.y >= 0f && courtPos.y <= HomographyMapper.COURT_LENGTH_M

        // Step 4: scoring
        return if (isIn) {
            if (courtPos.y < NET_Y_M) {
                bounceCountP1++
                if (bounceCountP1 >= 2) BounceEvent.PointAwarded(winner = 2, isOut = false, courtPos = courtPos)
                else null
            } else {
                bounceCountP2++
                if (bounceCountP2 >= 2) BounceEvent.PointAwarded(winner = 1, isOut = false, courtPos = courtPos)
                else null
            }
        } else {
            val winner = when {
                courtPos.y < 0f                                  -> 1
                courtPos.y > HomographyMapper.COURT_LENGTH_M     -> 2
                else                                             -> if (lastSide == 1) 2 else 1
            }
            BounceEvent.PointAwarded(winner = winner, isOut = true, courtPos = courtPos)
        }
    }

    fun reset() {
        previousVy     = 0f
        cooldownFrames = 0
        lastSide       = 0
        bounceCountP1  = 0
        bounceCountP2  = 0
    }
}
```

- [ ] **Step 5: Run the tests — verify all 12 PASS**

```
./gradlew testDebugUnitTest --tests "com.example.tennisscorer.tracking.BounceDetectorTest"
```

Expected: BUILD SUCCESSFUL, 12 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/tennisscorer/tracking/BounceEvent.kt \
        app/src/main/java/com/example/tennisscorer/tracking/BounceDetector.kt \
        app/src/test/java/com/example/tennisscorer/tracking/BounceDetectorTest.kt
git commit -m "feat(tracking): add BounceEvent and BounceDetector with rally state tracking"
```

---

### Task 2: BallTrackingViewModel — engine injection + bounce integration

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt`
- Modify: `app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt`

**Interfaces:**
- Consumes: `BounceEvent.PointAwarded` from Task 1
- Consumes: `BounceDetector.process()` and `BounceDetector.reset()` from Task 1
- Consumes: `TennisScoreEngine.pointWonBy(playerNum: Int)` — already exists in `TennisScoreEngine.kt`
- Consumes: `CalibrationState.Calibrated(mapper: HomographyMapper)` — already exists
- Produces: `BallTrackingViewModel(engine: TennisScoreEngine)` — updated constructor
- Produces: `BallTrackingViewModel.factory(engine: TennisScoreEngine): ViewModelProvider.Factory`
- Produces: `BallTrackingViewModel.handleBounceEvent(event: BounceEvent)` — `internal`, used by tests

---

- [ ] **Step 1: Write 2 failing tests — append to BallTrackingViewModelTest.kt**

First, update the existing `BallTrackingViewModelTest.kt` to use the new constructor (engine injection) AND add 2 new tests. Replace the entire file:

```kotlin
// app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import android.graphics.PointF
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.tracking.BounceEvent
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.TrackedBall
import io.mockk.any
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallTrackingViewModelTest {

    private val mockEngine = mockk<TennisScoreEngine>(relaxed = true)
    private val vm = BallTrackingViewModel(mockEngine)

    @After fun tearDown() { vm.cameraExecutor.shutdown() }

    @Test fun `permissionGranted starts false`() {
        assertFalse(vm.permissionGranted.value)
    }

    @Test fun `onPermissionResult true sets permissionGranted true`() {
        vm.onPermissionResult(true)
        assertTrue(vm.permissionGranted.value)
    }

    @Test fun `onPermissionResult false keeps permissionGranted false`() {
        vm.onPermissionResult(true)
        vm.onPermissionResult(false)
        assertFalse(vm.permissionGranted.value)
    }

    @Test fun `cameraError starts null`() {
        assertNull(vm.cameraError.value)
    }

    @Test fun `onCameraError sets cameraError message`() {
        vm.onCameraError("Kamera tidak dapat dibuka")
        assertEquals("Kamera tidak dapat dibuka", vm.cameraError.value)
    }

    @Test fun `cameraExecutor not null`() {
        assertNotNull(vm.cameraExecutor)
    }

    @Test fun `detections starts empty`() {
        assertTrue(vm.detections.value.isEmpty())
    }

    @Test fun `calibrationState starts Uncalibrated`() {
        assertTrue(vm.calibrationState.value is CalibrationState.Uncalibrated)
    }

    @Test fun `initCalibration transitions to Calibrating`() {
        val ctx = mockk<Context>(relaxed = true)
        vm.initCalibration(ctx)
        assertTrue(vm.calibrationState.value is CalibrationState.Calibrating)
    }

    @Test fun `initCalibration is idempotent`() {
        val ctx = mockk<Context>(relaxed = true)
        vm.initCalibration(ctx)
        vm.initCalibration(ctx)
        assertTrue(vm.calibrationState.value is CalibrationState.Calibrating)
    }

    @Test fun `trackedBall starts null`() {
        assertNull(vm.trackedBall.value)
    }

    @Test fun `resetTrajectory sets trackedBall to null`() {
        vm.resetTrajectory()
        assertNull(vm.trackedBall.value)
    }

    // --- Task 2 new tests ---

    @Test fun `handleBounceEvent PointAwarded calls engine pointWonBy and nulls trackedBall`() {
        val courtPos = PointF().also { it.x = 5f; it.y = 3f }
        vm.handleBounceEvent(BounceEvent.PointAwarded(winner = 2, isOut = false, courtPos = courtPos))
        verify { mockEngine.pointWonBy(2) }
        assertNull(vm.trackedBall.value)
    }

    @Test fun `bounce without calibration does not call pointWonBy`() {
        // calibrationState = Uncalibrated (default) → mapper=null → courtPos=null
        // → bounceDetector.process(vy, null, ...) returns null → engine never called
        // Send 20 identical detections — vy stays near 0, courtPos=null regardless
        repeat(20) { vm.processBallUpdate(null) }
        verify(exactly = 0) { mockEngine.pointWonBy(any()) }
    }
}
```

- [ ] **Step 2: Run the tests — verify the 2 new tests FAIL**

```
./gradlew testDebugUnitTest --tests "com.example.tennisscorer.ui.viewmodels.BallTrackingViewModelTest"
```

Expected: 12 existing tests FAIL (constructor mismatch), 2 new tests FAIL (methods don't exist yet). That's expected — we fix it in the next step.

- [ ] **Step 3: Replace BallTrackingViewModel.kt with updated version**

```kotlin
// app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt
package com.example.tennisscorer.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.tracking.BallDetector
import com.example.tennisscorer.tracking.BounceDetector
import com.example.tennisscorer.tracking.BounceEvent
import com.example.tennisscorer.tracking.CalibrationState
import com.example.tennisscorer.tracking.CourtDetector
import com.example.tennisscorer.tracking.Detection
import com.example.tennisscorer.tracking.FrameAnalyzer
import com.example.tennisscorer.tracking.HomographyMapper
import com.example.tennisscorer.tracking.HomographyResult
import com.example.tennisscorer.tracking.ImageAnalyzer
import com.example.tennisscorer.tracking.KalmanTracker
import com.example.tennisscorer.tracking.TrackedBall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BallTrackingViewModel(
    private val engine: TennisScoreEngine
) : ViewModel() {

    companion object {
        fun factory(engine: TennisScoreEngine): ViewModelProvider.Factory = viewModelFactory {
            initializer { BallTrackingViewModel(engine) }
        }
    }

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Uncalibrated)
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    private val _trackedBall = MutableStateFlow<TrackedBall?>(null)
    val trackedBall: StateFlow<TrackedBall?> = _trackedBall.asStateFlow()

    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageAnalyzer: ImageAnalyzer = ImageAnalyzer()

    private val kalmanTracker = KalmanTracker()
    private val bounceDetector = BounceDetector()
    private var ballDetector: BallDetector? = null
    private var courtDetector: CourtDetector? = null

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

    internal fun processBallUpdate(detection: Detection?) {
        val trackedBall = kalmanTracker.update(detection)
        _trackedBall.value = trackedBall
        if (trackedBall != null) {
            val mapper = (_calibrationState.value as? CalibrationState.Calibrated)?.mapper
            val courtPos = mapper?.mapToCourtCoords(trackedBall.position)
            val event = bounceDetector.process(trackedBall.velocity.y, courtPos, trackedBall.isPredicted)
            if (event is BounceEvent.PointAwarded) {
                handleBounceEvent(event)
            }
        }
    }

    internal fun handleBounceEvent(event: BounceEvent) {
        if (event is BounceEvent.PointAwarded) {
            engine.pointWonBy(event.winner)
            kalmanTracker.reset()
            bounceDetector.reset()
            _trackedBall.value = null
        }
    }

    fun initDetector(context: Context) {
        if (ballDetector != null) return
        val detector = BallDetector(context.applicationContext) { detections ->
            _detections.value = detections
            processBallUpdate(detections.firstOrNull())
        }
        ballDetector = detector
        setFrameAnalyzer(detector)
    }

    fun resetTrajectory() {
        kalmanTracker.reset()
        bounceDetector.reset()
        _trackedBall.value = null
    }

    fun initCalibration(context: Context) {
        if (_calibrationState.value != CalibrationState.Uncalibrated) return
        val appContext = context.applicationContext
        _calibrationState.value = CalibrationState.Calibrating
        val detector = CourtDetector { result ->
            when (result) {
                is HomographyResult.Success ->
                    _calibrationState.value = CalibrationState.Calibrated(HomographyMapper(result.matrix))
                is HomographyResult.Failed ->
                    _calibrationState.value = CalibrationState.Failed(result.reason)
            }
            initDetector(appContext)
        }
        courtDetector = detector
        setFrameAnalyzer(detector)
    }

    override fun onCleared() {
        cameraExecutor.shutdown()
        courtDetector?.close()
        ballDetector?.close()
        super.onCleared()
    }
}
```

- [ ] **Step 4: Update CameraScreen.kt — add `engine` parameter and ViewModel factory**

Change the function signature and `viewModel()` call. Find these lines in `CameraScreen.kt`:

```kotlin
// BEFORE (lines 40-43):
@Composable
fun CameraScreen(
    viewModel: BallTrackingViewModel = viewModel(),
    onBack: () -> Unit
)
```

Replace with:

```kotlin
// AFTER:
@Composable
fun CameraScreen(
    engine: TennisScoreEngine,
    viewModel: BallTrackingViewModel = viewModel(factory = BallTrackingViewModel.factory(engine)),
    onBack: () -> Unit
)
```

Also add the import at the top of `CameraScreen.kt` with the other imports:

```kotlin
import com.example.tennisscorer.TennisScoreEngine
```

- [ ] **Step 5: Update AppNavigation.kt — pass engine to CameraScreen**

Find this block in `AppNavigation.kt`:

```kotlin
// BEFORE:
composable(Screen.BallTracking.route) {
    CameraScreen(onBack = { navController.popBackStack() })
}
```

Replace with:

```kotlin
// AFTER:
composable(Screen.BallTracking.route) {
    CameraScreen(engine = engine, onBack = { navController.popBackStack() })
}
```

`engine` is already available in `AppNavigation` as a function parameter — no import change needed.

- [ ] **Step 6: Run all tests — verify 34 pass**

```
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 34 tests pass (12 existing BounceDetectorTest + 12 existing BallTrackingViewModelTest + 2 new BallTrackingViewModelTest = 34; KalmanTrackerTest's 6 tests are included in this count as part of the total from prior sub-projects).

Wait — the running total is:
- KalmanTrackerTest: 6
- BallTrackingViewModelTest: 14 (12 existing + 2 new)
- BounceDetectorTest: 12
- (Other earlier tests from sub-projects A/B/C: ~14)

The exact total will match whatever `./gradlew testDebugUnitTest` reports. Confirm it is ≥ 34 and all pass.

- [ ] **Step 7: Verify the app builds**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (no compilation errors).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModel.kt \
        app/src/main/java/com/example/tennisscorer/ui/screens/CameraScreen.kt \
        app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt \
        app/src/test/java/com/example/tennisscorer/ui/viewmodels/BallTrackingViewModelTest.kt
git commit -m "feat(tracking): inject TennisScoreEngine into BallTrackingViewModel, add bounce detection integration"
```
