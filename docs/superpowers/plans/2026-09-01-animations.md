# Animation Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tambah animasi bermakna di seluruh app TennisScorer — splash entrance, nav transitions, score pop, winner overlay, dan PlayerCard slide-in.

**Architecture:** Semua animasi menggunakan Compose Animation API bawaan (`Animatable`, `AnimatedContent`, `AnimatedVisibility`, `rememberInfiniteTransition`). Tidak ada dependency baru. Setiap task mengubah satu file saja.

**Tech Stack:** Jetpack Compose Animation (`androidx.compose.animation.*`, `androidx.compose.animation.core.*`), Navigation Compose 2.8.5

## Global Constraints

- Tidak ada library animasi pihak ketiga — hanya `androidx.compose.animation.*` dan `androidx.compose.animation.core.*`
- Tidak ada perubahan pada `TennisScoreEngine.kt` atau `TennisScoreState.kt`
- Semua warna tetap menggunakan token dari `ui/theme/Color.kt` — tidak ada inline hex baru
- Durasi animasi tidak boleh melebihi 600ms per spec
- Package: `com.example.tennisscorer`

---

### Task 1: Splash Screen — Entrance Staggered + Bouncing Ball

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt`

**Interfaces:**
- Consumes: `TennisAppIcon(size: Dp)`, `AppBg`, `CyanAccent` (tidak berubah)
- Produces: `SplashScreen(onStart: () -> Unit)` — signature tidak berubah, hanya animasi ditambahkan

- [ ] **Step 1: Tulis tes kompilasi awal**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` — pastikan baseline bersih sebelum mulai.

- [ ] **Step 2: Ganti isi `SplashScreen.kt` dengan versi beranimasi**

```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.components.TennisAppIcon
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.CyanAccent
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onStart: () -> Unit) {
    val iconScale    = remember { Animatable(0.5f) }
    val iconAlpha    = remember { Animatable(0f) }
    val titleAlpha   = remember { Animatable(0f) }
    val titleOffset  = remember { Animatable(20f) }
    val subAlpha     = remember { Animatable(0f) }
    val subOffset    = remember { Animatable(20f) }
    val btnAlpha     = remember { Animatable(0f) }
    val btnScale     = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        launch { iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        launch { iconAlpha.animateTo(1f, tween(300)) }
        delay(150)
        launch { titleAlpha.animateTo(1f, tween(300)) }
        launch { titleOffset.animateTo(0f, tween(300)) }
        delay(130)
        launch { subAlpha.animateTo(1f, tween(300)) }
        launch { subOffset.animateTo(0f, tween(300)) }
        delay(140)
        launch { btnAlpha.animateTo(1f, tween(250)) }
        launch { btnScale.animateTo(1f, tween(250)) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ball")
    val ballY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 72f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowIn),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ballY"
    )
    val squishProgress = ballY / 72f
    val ballScaleX = 1f + 0.3f * squishProgress
    val ballScaleY = 1f - 0.3f * squishProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        Text(
            text = "🎾",
            fontSize = 28.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 48.dp, y = (ballY - 88).dp)
                .graphicsLayer(scaleX = ballScaleX, scaleY = ballScaleY)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .scale(iconScale.value)
                    .graphicsLayer(alpha = iconAlpha.value)
            ) {
                TennisAppIcon(size = 120.dp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Tennis Score",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .graphicsLayer(alpha = titleAlpha.value)
                    .offset(y = titleOffset.value.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Smart Court Scoreboard",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = CyanAccent,
                modifier = Modifier
                    .graphicsLayer(alpha = subAlpha.value)
                    .offset(y = subOffset.value.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .width(140.dp)
                    .height(44.dp)
                    .scale(btnScale.value)
                    .graphicsLayer(alpha = btnAlpha.value),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text(text = "Start", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
```

- [ ] **Step 3: Verifikasi kompilasi dan tes**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt
git commit -m "feat(anim): splash entrance staggered + bouncing ball"
```

---

### Task 2: Navigation — Slide Transitions Antar Screen

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt`

**Interfaces:**
- Consumes: `Screen.Splash`, `Screen.Input`, `Screen.Game`, `SplashScreen`, `PlayerInputScreen`, `ScoreboardScreen` (tidak berubah)
- Produces: `AppNavigation(engine: TennisScoreEngine)` — signature tidak berubah

- [ ] **Step 1: Ganti isi `AppNavigation.kt` dengan versi beranimasi**

```kotlin
package com.example.tennisscorer.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.ui.screens.PlayerInputScreen
import com.example.tennisscorer.ui.screens.ScoreboardScreen
import com.example.tennisscorer.ui.screens.SplashScreen

@Composable
fun AppNavigation(engine: TennisScoreEngine) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = {
            slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
        }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onStart = { navController.navigate(Screen.Input.route) })
        }
        composable(Screen.Input.route) {
            PlayerInputScreen(
                engine = engine,
                onBack = { navController.popBackStack() },
                onStartMatch = { navController.navigate(Screen.Game.route) }
            )
        }
        composable(Screen.Game.route) {
            ScoreboardScreen(
                engine = engine,
                onBackToInput = { navController.popBackStack() }
            )
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

- [ ] **Step 2: Verifikasi kompilasi dan tes**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt
git commit -m "feat(anim): slide transitions between screens"
```

---

### Task 3: Scoreboard — AnimatedContent Score + Flash Feedback

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/ScoreboardScreen.kt`

**Interfaces:**
- Consumes: `TennisScoreEngine`, `ScoreBadge`, `WinnerOverlay`, `ScoreRed`, `ScoreBlue`, `ActionBtnBg` (tidak berubah)
- Produces: `ScoreboardScreen(engine, onBackToInput)` — signature tidak berubah

- [ ] **Step 1: Ganti isi `ScoreboardScreen.kt` dengan versi beranimasi**

```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.ui.components.ScoreBadge
import com.example.tennisscorer.ui.components.WinnerOverlay
import com.example.tennisscorer.ui.theme.ActionBtnBg
import com.example.tennisscorer.ui.theme.ScoreBlue
import com.example.tennisscorer.ui.theme.ScoreRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScoreboardScreen(
    engine: TennisScoreEngine,
    onBackToInput: () -> Unit
) {
    val state by engine.scoreState.collectAsState()
    val scope = rememberCoroutineScope()

    val totalGames = state.p1Games + state.p2Games + state.p1Sets + state.p2Sets
    val isSwapped  = (totalGames % 2 != 0)

    val leftName     = if (!isSwapped) state.p1Name        else state.p2Name
    val leftScore    = if (!isSwapped) state.p1DisplayScore else state.p2DisplayScore
    val leftBgColor  = if (!isSwapped) ScoreRed             else ScoreBlue
    val leftPlayerId = if (!isSwapped) 1                    else 2

    val rightName     = if (!isSwapped) state.p2Name        else state.p1Name
    val rightScore    = if (!isSwapped) state.p2DisplayScore else state.p1DisplayScore
    val rightBgColor  = if (!isSwapped) ScoreBlue            else ScoreRed
    val rightPlayerId = if (!isSwapped) 2                    else 1

    var flashLeft  by remember { mutableStateOf(false) }
    var flashRight by remember { mutableStateOf(false) }

    val flashLeftAlpha  by animateFloatAsState(if (flashLeft)  0.28f else 0f, tween(100), label = "fl")
    val flashRightAlpha by animateFloatAsState(if (flashRight) 0.28f else 0f, tween(100), label = "fr")

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(leftBgColor)
                    .clickable(enabled = !state.isMatchFinished) {
                        engine.pointWonBy(leftPlayerId)
                        scope.launch {
                            flashLeft = true
                            delay(220)
                            flashLeft = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(leftName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = leftScore,
                        transitionSpec = {
                            (slideInVertically(tween(200)) { -it } + fadeIn(tween(200)))
                                .togetherWith(slideOutVertically(tween(200)) { it } + fadeOut(tween(200)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "leftScore"
                    ) { score ->
                        Text(score, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
                if (flashLeftAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = flashLeftAlpha))
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(rightBgColor)
                    .clickable(enabled = !state.isMatchFinished) {
                        engine.pointWonBy(rightPlayerId)
                        scope.launch {
                            flashRight = true
                            delay(220)
                            flashRight = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rightName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = rightScore,
                        transitionSpec = {
                            (slideInVertically(tween(200)) { -it } + fadeIn(tween(200)))
                                .togetherWith(slideOutVertically(tween(200)) { it } + fadeOut(tween(200)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "rightScore"
                    ) { score ->
                        Text(score, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
                if (flashRightAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = flashRightAlpha))
                    )
                }
            }
        }

        ScoreBadge(
            p1Sets = state.p1Sets,
            p2Sets = state.p2Sets,
            p1Games = state.p1Games,
            p2Games = state.p2Games,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBackToInput,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
            ) {
                Text("Change Player", fontSize = 12.sp, color = Color.White)
            }
            Button(
                onClick = { engine.resetScore() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
            ) {
                Text("Reset Game", fontSize = 12.sp, color = Color.White)
            }
        }

        if (state.isMatchFinished) {
            WinnerOverlay(
                winnerName = state.winnerName ?: "",
                onPlayAgain = { engine.resetScore() }
            )
        }
    }
}
```

- [ ] **Step 2: Verifikasi kompilasi dan tes**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/screens/ScoreboardScreen.kt
git commit -m "feat(anim): AnimatedContent score + white flash on point scored"
```

---

### Task 4: WinnerOverlay — Dramatic Entrance + Trophy Pulse

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/components/WinnerOverlay.kt`

**Interfaces:**
- Consumes: `CyanAccent`, `Gold`, `OverlayCardBg` (tidak berubah)
- Produces: `WinnerOverlay(winnerName: String, onPlayAgain: () -> Unit)` — signature tidak berubah

- [ ] **Step 1: Ganti isi `WinnerOverlay.kt` dengan versi beranimasi**

```kotlin
package com.example.tennisscorer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.theme.Gold
import com.example.tennisscorer.ui.theme.OverlayCardBg

@Composable
fun WinnerOverlay(winnerName: String, onPlayAgain: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val backdropAlpha by animateFloatAsState(
        targetValue = if (visible) 0.65f else 0f,
        animationSpec = tween(400),
        label = "backdrop"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "trophy")
    val trophyScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowIn),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophyScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.7f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(tween(300))
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = OverlayCardBg)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🏆", fontSize = 48.sp, modifier = Modifier.scale(trophyScale))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$winnerName Menang!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Gold
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onPlayAgain,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Main Lagi", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verifikasi kompilasi dan tes**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/components/WinnerOverlay.kt
git commit -m "feat(anim): WinnerOverlay scale entrance + trophy pulse"
```

---

### Task 5: PlayerInputScreen — PlayerCard Slide-in

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/PlayerInputScreen.kt`

**Interfaces:**
- Consumes: `PlayerCard`, `AppBg`, `CardBg`, `RedAccent`, `BlueAccent`, `VsGreen`, `VsBadgeBg`, `StartBtnBg` (tidak berubah)
- Produces: `PlayerInputScreen(engine, onBack, onStartMatch)` — signature tidak berubah

- [ ] **Step 1: Ganti isi `PlayerInputScreen.kt` dengan versi beranimasi**

```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.ui.components.PlayerCard
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.BlueAccent
import com.example.tennisscorer.ui.theme.CardBg
import com.example.tennisscorer.ui.theme.RedAccent
import com.example.tennisscorer.ui.theme.StartBtnBg
import com.example.tennisscorer.ui.theme.VsBadgeBg
import com.example.tennisscorer.ui.theme.VsGreen
import kotlinx.coroutines.delay

@Composable
fun PlayerInputScreen(
    engine: TennisScoreEngine,
    onBack: () -> Unit,
    onStartMatch: () -> Unit
) {
    var card1Visible by remember { mutableStateOf(false) }
    var card2Visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        card1Visible = true
        delay(100)
        card2Visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("<", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Persiapan Pertandingan",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = card1Visible,
                enter = slideInHorizontally(tween(400)) { -it } + fadeIn(tween(400)),
                modifier = Modifier.weight(1f)
            ) {
                PlayerCard(
                    playerNum = 1,
                    name = engine.p1NameInput,
                    onNameChange = { engine.updateP1Name(it) },
                    accentColor = RedAccent,
                    cornerLabel = "Red Corner"
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(VsBadgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "VS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = VsGreen)
            }

            AnimatedVisibility(
                visible = card2Visible,
                enter = slideInHorizontally(tween(400)) { it } + fadeIn(tween(400)),
                modifier = Modifier.weight(1f)
            ) {
                PlayerCard(
                    playerNum = 2,
                    name = engine.p2NameInput,
                    onNameChange = { engine.updateP2Name(it) },
                    accentColor = BlueAccent,
                    cornerLabel = "Blue Corner"
                )
            }
        }

        Button(
            onClick = {
                val p1 = engine.p1NameInput.ifBlank { "Pemain 1" }
                val p2 = engine.p2NameInput.ifBlank { "Pemain 2" }
                engine.resetMatch()
                engine.setPlayerNames(p1, p2)
                onStartMatch()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .width(200.dp)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StartBtnBg)
        ) {
            Text(text = "Start Game", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
```

- [ ] **Step 2: Verifikasi kompilasi dan tes**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/screens/PlayerInputScreen.kt
git commit -m "feat(anim): PlayerCard slide-in from left/right on screen enter"
```

---

### Task 6: Push ke GitHub

**Files:** Tidak ada perubahan file baru

- [ ] **Step 1: Verifikasi semua tes masih lulus**

Run:
```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Push ke remote**

```
git push origin master
```

Expected: GitHub Actions akan trigger build otomatis dan update `tennisscorer.apk` di Releases.
