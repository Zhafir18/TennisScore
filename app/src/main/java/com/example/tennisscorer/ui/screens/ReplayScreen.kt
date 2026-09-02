package com.example.tennisscorer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.components.ScoreBadge
import com.example.tennisscorer.ui.components.WinnerOverlay
import com.example.tennisscorer.ui.theme.ActionBtnBg
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.theme.ScoreBlue
import com.example.tennisscorer.ui.theme.ScoreRed
import com.example.tennisscorer.ui.viewmodels.ReplayViewModel

@Composable
fun ReplayScreen(
    matchId: Long,
    repository: MatchRepository,
    onBack: () -> Unit
) {
    val vm: ReplayViewModel = viewModel(
        key = "replay_$matchId",
        factory = ReplayViewModel.Factory(repository, matchId)
    )

    val state by vm.replayState.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val speed by vm.speedMultiplier.collectAsState()
    val isFinished by vm.isFinished.collectAsState()
    val progress by vm.progress.collectAsState()
    val record by vm.matchRecord.collectAsState()
    val loadError by vm.loadError.collectAsState()

    if (loadError) {
        Box(modifier = Modifier.fillMaxSize().background(AppBg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Data tidak tersedia", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)) {
                    Text("Kembali")
                }
            }
        }
        return
    }

    val totalGames = state.p1Games + state.p2Games + state.p1Sets + state.p2Sets
    val isSwapped = (totalGames % 2 != 0)

    val leftName  = if (!isSwapped) state.p1Name        else state.p2Name
    val leftScore = if (!isSwapped) state.p1DisplayScore else state.p2DisplayScore
    val leftBg    = if (!isSwapped) ScoreRed             else ScoreBlue

    val rightName  = if (!isSwapped) state.p2Name        else state.p1Name
    val rightScore = if (!isSwapped) state.p2DisplayScore else state.p1DisplayScore
    val rightBg    = if (!isSwapped) ScoreBlue            else ScoreRed

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(leftBg),
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
                        label = "replayLeft"
                    ) { score ->
                        Text(score, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(rightBg),
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
                        label = "replayRight"
                    ) { score ->
                        Text(score, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }

        ScoreBadge(
            p1Sets = state.p1Sets,
            p2Sets = state.p2Sets,
            p1Games = state.p1Games,
            p2Games = state.p2Games,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )

        // Top banner
        Text(
            text = "⏪ Replay — ${record?.p1Name ?: ""} vs ${record?.p2Name ?: ""}",
            fontSize = 11.sp,
            color = CyanAccent,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
        )

        // Bottom control bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
            ) {
                Text("← Kembali", fontSize = 12.sp, color = Color.White)
            }

            Button(
                onClick = { vm.togglePlayPause() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                enabled = !isFinished
            ) {
                Text(if (isPlaying) "⏸ Pause" else "▶ Play", fontSize = 12.sp, color = Color.White)
            }

            listOf(0.5f to "0.5×", 1.0f to "1×", 2.0f to "2×").forEach { (value, label) ->
                FilterChip(
                    selected = speed == value,
                    onClick = { vm.setSpeed(value) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }

            if (progress.second > 0) {
                Text(
                    text = "Poin ${progress.first} / ${progress.second}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        if (isFinished) {
            WinnerOverlay(
                winnerName = state.winnerName ?: record?.winnerName ?: "",
                onPlayAgain = onBack
            )
        }
    }
}
