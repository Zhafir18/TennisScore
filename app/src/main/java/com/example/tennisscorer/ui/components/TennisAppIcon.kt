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
import com.example.tennisscorer.ui.theme.IconBlue
import com.example.tennisscorer.ui.theme.IconCenter
import com.example.tennisscorer.ui.theme.IconRed

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
                    .background(IconRed)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(IconBlue)
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.44f)
                .clip(CircleShape)
                .background(IconCenter),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎾", fontSize = (size.value * 0.23f).sp)
        }
    }
}
