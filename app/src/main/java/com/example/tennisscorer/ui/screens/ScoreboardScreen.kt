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
