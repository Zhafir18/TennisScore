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
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onStart: () -> Unit, onHistory: () -> Unit) {
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
            animation = tween(500, easing = LinearOutSlowInEasing),
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
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier
                    .width(140.dp)
                    .height(40.dp)
                    .scale(btnScale.value)
                    .graphicsLayer(alpha = btnAlpha.value),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
            ) {
                Text(text = "Riwayat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CyanAccent)
            }
        }
    }
}
