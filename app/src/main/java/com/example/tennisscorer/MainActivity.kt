package com.example.tennisscorer

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.ui.theme.TennisScorerTheme

// --- Design Tokens ---
private val AppBg        = Color(0xFF050A1A)
private val CardBg       = Color(0xFF0F1629)
private val CyanAccent   = Color(0xFF00B8D9)
private val RedAccent    = Color(0xFFDC2626)
private val BlueAccent   = Color(0xFF818CF8)
private val ScoreRed     = Color(0xFF6B0F0F)
private val ScoreBlue    = Color(0xFF1B1B52)
private val VsGreen      = Color(0xFFB5FF00)
private val Gold         = Color(0xFFFFD700)

class MainActivity : ComponentActivity() {
    private val scoreEngine: TennisScoreEngine by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TennisScorerTheme {
                TennisApp(engine = scoreEngine)
            }
        }
    }
}

@Composable
fun TennisApp(engine: TennisScoreEngine) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

    var currentScreen by rememberSaveable { mutableStateOf("splash") }
    var p1NameInput by rememberSaveable { mutableStateOf("") }
    var p2NameInput by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        when (currentScreen) {
            "splash" -> SplashScreen(onStart = { currentScreen = "input" })
            "input"  -> PlayerInputScreen(
                p1Name = p1NameInput,
                p2Name = p2NameInput,
                onP1NameChange = { p1NameInput = it },
                onP2NameChange = { p2NameInput = it },
                onBack = { currentScreen = "splash" },
                onStartMatch = {
                    val p1 = p1NameInput.ifBlank { "Pemain 1" }
                    val p2 = p2NameInput.ifBlank { "Pemain 2" }
                    engine.resetMatch()
                    engine.setPlayerNames(p1, p2)
                    currentScreen = "game"
                }
            )
            "game"   -> LandscapeTennisScoreboard(
                engine = engine,
                onBackToInput = { currentScreen = "input" }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// LAYAR 1: SPLASH / LANDING
// -----------------------------------------------------------------------------
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
            modifier = Modifier
                .width(140.dp)
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
            Text(
                text = "Start",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

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

// -----------------------------------------------------------------------------
// LAYAR 2: INPUT NAMA PEMAIN (LANDSCAPE)
// -----------------------------------------------------------------------------
@Composable
fun PlayerInputScreen(
    p1Name: String,
    p2Name: String,
    onP1NameChange: (String) -> Unit,
    onP2NameChange: (String) -> Unit,
    onBack: () -> Unit,
    onStartMatch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        // Tombol back kiri atas
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

        // Judul atas tengah
        Text(
            text = "Persiapan Pertandingan",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        )

        // Dua kartu pemain + VS di tengah
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
                name = p1Name,
                onNameChange = onP1NameChange,
                accentColor = RedAccent,
                cornerLabel = "Red Corner",
                modifier = Modifier.weight(1f)
            )

            // Badge VS
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A2035)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VsGreen
                )
            }

            PlayerCard(
                playerNum = 2,
                name = p2Name,
                onNameChange = onP2NameChange,
                accentColor = BlueAccent,
                cornerLabel = "Blue Corner",
                modifier = Modifier.weight(1f)
            )
        }

        // Tombol Start Game bawah tengah
        Button(
            onClick = onStartMatch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .width(200.dp)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
        ) {
            Text(
                text = "Start Game",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

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

// -----------------------------------------------------------------------------
// LAYAR 3: PAPAN SKOR LANDSCAPE
// -----------------------------------------------------------------------------
@Composable
fun LandscapeTennisScoreboard(
    engine: TennisScoreEngine,
    onBackToInput: () -> Unit
) {
    val state by engine.scoreState.collectAsState()

    val totalGames = state.p1Games + state.p2Games + state.p1Sets + state.p2Sets
    val isSwapped = (totalGames % 2 != 0)

    val leftName     = if (!isSwapped) state.p1Name        else state.p2Name
    val leftScore    = if (!isSwapped) state.p1DisplayScore else state.p2DisplayScore
    val leftBgColor  = if (!isSwapped) ScoreRed             else ScoreBlue
    val leftPlayerId = if (!isSwapped) 1                    else 2

    val rightName     = if (!isSwapped) state.p2Name        else state.p1Name
    val rightScore    = if (!isSwapped) state.p2DisplayScore else state.p1DisplayScore
    val rightBgColor  = if (!isSwapped) ScoreBlue            else ScoreRed
    val rightPlayerId = if (!isSwapped) 2                    else 1

    Box(modifier = Modifier.fillMaxSize()) {
        // Dua sisi warna pemain
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
                    Text(
                        text = leftName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = leftScore,
                        fontSize = 100.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
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
                    Text(
                        text = rightName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = rightScore,
                        fontSize = 100.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        // Badge Match / Game atas tengah
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC000000))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Match", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("${state.p1Sets} : ${state.p2Sets}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Game", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("${state.p1Games} : ${state.p2Games}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Tombol aksi bawah tengah
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

        // Overlay pemenang
        if (state.isMatchFinished) {
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
                            text = "${state.winnerName} Menang!",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Gold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { engine.resetScore() },
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
}

@Composable
fun LockScreenOrientation(orientation: Int) {
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
