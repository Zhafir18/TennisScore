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
            animation = tween(800, easing = LinearOutSlowInEasing),
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
