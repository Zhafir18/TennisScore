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
