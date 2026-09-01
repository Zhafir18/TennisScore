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
import com.example.tennisscorer.ui.theme.BadgeBg
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
        colors = CardDefaults.cardColors(containerColor = BadgeBg)
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
