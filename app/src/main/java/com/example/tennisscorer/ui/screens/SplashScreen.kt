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
