# Component-Based Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pecah `MainActivity.kt` monolitik menjadi struktur component-based dengan Navigation Compose.

**Architecture:** Screens dipisah ke `ui/screens/`, komponen reusable ke `ui/components/`, navigasi pakai `NavController` + `NavHost` menggantikan `currentScreen` string, state nama pemain dipindah ke ViewModel.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose 2.8.5, Material3, ViewModel

## Global Constraints

- Package root: `com.example.tennisscorer`
- Min SDK: 24, Target SDK: 37, Compile SDK: 37
- Compose BOM: `2026.02.01`
- Navigation Compose: `2.8.5` (tidak di-manage BOM, versi eksplisit)
- Tidak ada perubahan logika scoring — hanya refactor struktur
- Semua file baru menggunakan design tokens dari `ui/theme/Color.kt`

---

## File Map

| File | Action | Tanggung Jawab |
|------|--------|----------------|
| `gradle/libs.versions.toml` | Modify | Tambah versi + library navigation-compose |
| `app/build.gradle.kts` | Modify | Tambah dependency navigation-compose |
| `navigation/Screen.kt` | Create | Sealed class route definitions |
| `navigation/AppNavigation.kt` | Create | NavHost + LockScreenOrientation |
| `ui/theme/Color.kt` | Modify | Tambah semua design tokens |
| `data/TennisScoreEngine.kt` | Modify | Tambah p1NameInput, p2NameInput, updateP1Name, updateP2Name |
| `ui/components/TennisAppIcon.kt` | Create | Ikon splash screen |
| `ui/components/PlayerCard.kt` | Create | Kartu input pemain |
| `ui/components/ScoreBadge.kt` | Create | Badge Match/Game |
| `ui/components/WinnerOverlay.kt` | Create | Overlay pemenang |
| `ui/screens/SplashScreen.kt` | Create | Layar splash |
| `ui/screens/PlayerInputScreen.kt` | Create | Layar input nama pemain |
| `ui/screens/ScoreboardScreen.kt` | Create | Layar papan skor |
| `MainActivity.kt` | Modify | Hanya setContent + viewModel, hapus semua composable lama |

---

### Task 1: Tambah Dependency Navigation Compose

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `libs.androidx.navigation.compose` tersedia untuk di-import di task berikutnya

- [ ] **Step 1: Tambah versi dan library ke libs.versions.toml**

Tambah di bagian `[versions]`:
```toml
navigationCompose = "2.8.5"
```

Tambah di bagian `[libraries]`:
```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

File lengkap `gradle/libs.versions.toml` setelah perubahan:
```toml
[versions]
agp = "9.3.2"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
navigationCompose = "2.8.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Tambah implementation ke app/build.gradle.kts**

Di blok `dependencies {}`, tambah setelah `implementation(libs.androidx.lifecycle.runtime.ktx)`:
```kotlin
implementation(libs.androidx.navigation.compose)
```

- [ ] **Step 3: Sync gradle dan verifikasi**

Di Android Studio: **File > Sync Project with Gradle Files**

Expected: Build sync berhasil tanpa error. Navigation Compose tersedia.

---

### Task 2: Design Tokens + Screen Routes

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/ui/theme/Color.kt`
- Create: `app/src/main/java/com/example/tennisscorer/navigation/Screen.kt`

**Interfaces:**
- Produces: Token warna `AppBg`, `CardBg`, `CyanAccent`, `RedAccent`, `BlueAccent`, `ScoreRed`, `ScoreBlue`, `VsGreen`, `Gold` dari package `com.example.tennisscorer.ui.theme`
- Produces: `Screen.Splash.route`, `Screen.Input.route`, `Screen.Game.route` dari package `com.example.tennisscorer.navigation`

- [ ] **Step 1: Tambah design tokens ke Color.kt**

Isi lengkap file `app/src/main/java/com/example/tennisscorer/ui/theme/Color.kt`:
```kotlin
package com.example.tennisscorer.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// App Design Tokens
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

- [ ] **Step 2: Buat folder navigation dan file Screen.kt**

Buat file `app/src/main/java/com/example/tennisscorer/navigation/Screen.kt`:
```kotlin
package com.example.tennisscorer.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Input  : Screen("input")
    object Game   : Screen("game")
}
```

---

### Task 3: Update ViewModel dengan Name Input State

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/TennisScoreEngine.kt`

**Interfaces:**
- Consumes: `androidx.compose.runtime.mutableStateOf`, `getValue`, `setValue`
- Produces: `engine.p1NameInput: String`, `engine.p2NameInput: String`, `engine.updateP1Name(String)`, `engine.updateP2Name(String)`

- [ ] **Step 1: Tambah import dan state ke TennisScoreEngine**

Tambah imports di bagian atas (setelah imports yang ada):
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

Tambah properties tepat setelah deklarasi `val scoreState`:
```kotlin
var p1NameInput by mutableStateOf("")
    private set
var p2NameInput by mutableStateOf("")
    private set
```

Tambah dua fungsi setelah `setPlayerNames`:
```kotlin
fun updateP1Name(name: String) { p1NameInput = name }
fun updateP2Name(name: String) { p2NameInput = name }
```

File lengkap `TennisScoreEngine.kt` setelah perubahan:
```kotlin
package com.example.tennisscorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TennisScoreEngine : ViewModel() {

    private val _scoreState = MutableStateFlow(TennisScoreState())
    val scoreState: StateFlow<TennisScoreState> = _scoreState.asStateFlow()

    var p1NameInput by mutableStateOf("")
        private set
    var p2NameInput by mutableStateOf("")
        private set

    fun pointWonBy(playerNum: Int) {
        val current = _scoreState.value
        if (current.isMatchFinished) return

        var p1P = current.p1Points
        var p2P = current.p2Points
        var p1G = current.p1Games
        var p2G = current.p2Games
        var p1S = current.p1Sets
        var p2S = current.p2Sets
        var isTb = current.isTiebreak

        if (playerNum == 1) p1P++ else p2P++

        if (isTb) {
            if (p1P >= 7 && (p1P - p2P) >= 2) {
                p1S++
                p1G = 0; p2G = 0; p1P = 0; p2P = 0
                isTb = false
            } else if (p2P >= 7 && (p2P - p1P) >= 2) {
                p2S++
                p1G = 0; p2G = 0; p1P = 0; p2P = 0
                isTb = false
            }
        } else {
            if (p1P >= 4 && (p1P - p2P) >= 2) {
                p1G++
                p1P = 0; p2P = 0
            } else if (p2P >= 4 && (p2P - p1P) >= 2) {
                p2G++
                p1P = 0; p2P = 0
            } else if (p1P >= 3 && p2P >= 3 && p1P == p2P && p1P > 3) {
                p1P = 3; p2P = 3
            }
        }

        if (p1G == 6 && p2G == 6 && !isTb) {
            isTb = true
        } else if (p1G >= 6 && (p1G - p2G) >= 2) {
            p1S++
            p1G = 0; p2G = 0; p1P = 0; p2P = 0
            isTb = false
        } else if (p2G >= 6 && (p2G - p1G) >= 2) {
            p2S++
            p1G = 0; p2G = 0; p1P = 0; p2P = 0
            isTb = false
        }

        var finished = false
        var winner: String? = null
        if (p1S == 2) { finished = true; winner = current.p1Name }
        else if (p2S == 2) { finished = true; winner = current.p2Name }

        _scoreState.update {
            it.copy(
                p1Points = p1P, p2Points = p2P,
                p1Games = p1G, p2Games = p2G,
                p1Sets = p1S, p2Sets = p2S,
                isTiebreak = isTb,
                isMatchFinished = finished,
                winnerName = winner
            )
        }
    }

    fun resetMatch() {
        _scoreState.value = TennisScoreState()
    }

    fun resetScore() {
        val current = _scoreState.value
        _scoreState.value = TennisScoreState(p1Name = current.p1Name, p2Name = current.p2Name)
    }

    fun setPlayerNames(p1: String, p2: String) {
        _scoreState.update { it.copy(p1Name = p1, p2Name = p2) }
    }

    fun updateP1Name(name: String) { p1NameInput = name }
    fun updateP2Name(name: String) { p2NameInput = name }
}
```

---

### Task 4: Buat ui/components/

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/ui/components/TennisAppIcon.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/components/PlayerCard.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/components/ScoreBadge.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/components/WinnerOverlay.kt`

**Interfaces:**
- Consumes: Token warna dari `com.example.tennisscorer.ui.theme` (Task 2)
- Produces: `TennisAppIcon(size: Dp)`, `PlayerCard(playerNum, name, onNameChange, accentColor, cornerLabel, modifier)`, `ScoreBadge(p1Sets, p2Sets, p1Games, p2Games, modifier)`, `WinnerOverlay(winnerName, onPlayAgain)`

- [ ] **Step 1: Buat TennisAppIcon.kt**

```kotlin
package com.example.tennisscorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TennisAppIcon(size: Dp = 120.dp) {
    val cornerRadius = RoundedCornerShape((size.value * 0.22f).dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(cornerRadius)
            .border(2.dp, Color.White.copy(alpha = 0.08f), cornerRadius),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF8B1A1A))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1D4ED8))
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.44f)
                .clip(CircleShape)
                .background(Color(0xFF0D0900)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎾", fontSize = (size.value * 0.23f).sp)
        }
    }
}
```

- [ ] **Step 2: Buat PlayerCard.kt**

```kotlin
package com.example.tennisscorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.theme.CardBg

@Composable
fun PlayerCard(
    playerNum: Int,
    name: String,
    onNameChange: (String) -> Unit,
    accentColor: Color,
    cornerLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Player $playerNum",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = cornerLabel,
                    fontSize = 11.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "Player Name", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFE2E8F0),
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A)
                )
            )
        }
    }
}
```

- [ ] **Step 3: Buat ScoreBadge.kt**

```kotlin
package com.example.tennisscorer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.theme.Gold

@Composable
fun ScoreBadge(
    p1Sets: Int,
    p2Sets: Int,
    p1Games: Int,
    p2Games: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC000000))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Match", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text("$p1Sets : $p2Sets", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Game", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text("$p1Games : $p2Games", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
```

- [ ] **Step 4: Buat WinnerOverlay.kt**

```kotlin
package com.example.tennisscorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.theme.Gold

@Composable
fun WinnerOverlay(winnerName: String, onPlayAgain: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🏆", fontSize = 48.sp)
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
```

---

### Task 5: Buat ui/screens/

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/screens/PlayerInputScreen.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/screens/ScoreboardScreen.kt`

**Interfaces:**
- Consumes: `TennisAppIcon` dari `ui.components`, `PlayerCard`, `ScoreBadge`, `WinnerOverlay` (Task 4)
- Consumes: `TennisScoreEngine.p1NameInput`, `p2NameInput`, `updateP1Name`, `updateP2Name` (Task 3)
- Consumes: Token warna dari `ui.theme` (Task 2)
- Produces: `SplashScreen(onStart: () -> Unit)`, `PlayerInputScreen(engine, onBack, onStartMatch)`, `ScoreboardScreen(engine, onBackToInput)`

- [ ] **Step 1: Buat SplashScreen.kt**

```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.components.TennisAppIcon
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.CyanAccent

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TennisAppIcon(size = 120.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Tennis Score",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Smart Court Scoreboard",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = CyanAccent
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.width(140.dp).height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
            Text(text = "Start", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
```

- [ ] **Step 2: Buat PlayerInputScreen.kt**

```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.tennisscorer.ui.theme.VsGreen

@Composable
fun PlayerInputScreen(
    engine: TennisScoreEngine,
    onBack: () -> Unit,
    onStartMatch: () -> Unit
) {
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
            PlayerCard(
                playerNum = 1,
                name = engine.p1NameInput,
                onNameChange = { engine.updateP1Name(it) },
                accentColor = RedAccent,
                cornerLabel = "Red Corner",
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A2035)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "VS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = VsGreen)
            }
            PlayerCard(
                playerNum = 2,
                name = engine.p2NameInput,
                onNameChange = { engine.updateP2Name(it) },
                accentColor = BlueAccent,
                cornerLabel = "Blue Corner",
                modifier = Modifier.weight(1f)
            )
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
        ) {
            Text(text = "Start Game", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
```

- [ ] **Step 3: Buat ScoreboardScreen.kt**

```kotlin
package com.example.tennisscorer.ui.screens

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
import com.example.tennisscorer.ui.theme.ScoreBlue
import com.example.tennisscorer.ui.theme.ScoreRed

@Composable
fun ScoreboardScreen(
    engine: TennisScoreEngine,
    onBackToInput: () -> Unit
) {
    val state by engine.scoreState.collectAsState()

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

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(leftBgColor)
                    .clickable(enabled = !state.isMatchFinished) { engine.pointWonBy(leftPlayerId) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = leftName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = leftScore, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(rightBgColor)
                    .clickable(enabled = !state.isMatchFinished) { engine.pointWonBy(rightPlayerId) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = rightName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = rightScore, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xBB000000))
            ) {
                Text("Change Player", fontSize = 12.sp, color = Color.White)
            }
            Button(
                onClick = { engine.resetScore() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xBB000000))
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

---

### Task 6: AppNavigation + Simplify MainActivity

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/MainActivity.kt`

**Interfaces:**
- Consumes: `Screen.Splash/Input/Game.route` (Task 2), semua screens (Task 5), `TennisScoreEngine` (Task 3)
- Produces: App berjalan dengan navigation yang proper

- [ ] **Step 1: Buat AppNavigation.kt**

```kotlin
package com.example.tennisscorer.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
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
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
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

- [ ] **Step 2: Replace isi MainActivity.kt**

Ganti seluruh isi `MainActivity.kt` dengan:
```kotlin
package com.example.tennisscorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.tennisscorer.navigation.AppNavigation
import com.example.tennisscorer.ui.theme.TennisScorerTheme

class MainActivity : ComponentActivity() {
    private val engine: TennisScoreEngine by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TennisScorerTheme {
                AppNavigation(engine = engine)
            }
        }
    }
}
```

- [ ] **Step 3: Build dan verifikasi**

Di Android Studio: **Build > Make Project** (Ctrl+F9)

Expected: BUILD SUCCESSFUL — 0 errors.

Jika error `Unresolved reference: navigation-compose`, pastikan gradle sync sudah dijalankan di Task 1.

- [ ] **Step 4: Jalankan app di emulator/device dan verifikasi alur navigasi**

Cek:
- [ ] Splash screen muncul saat app dibuka
- [ ] Tombol "Start" navigasi ke Input screen
- [ ] Tombol "<" di Input navigasi kembali ke Splash
- [ ] Tombol "Start Game" navigasi ke Scoreboard
- [ ] Tombol "Change Player" navigasi kembali ke Input
- [ ] Skor tetap saat kembali ke Input (tidak reset)
- [ ] "Reset Game" mereset skor tapi mempertahankan nama pemain
