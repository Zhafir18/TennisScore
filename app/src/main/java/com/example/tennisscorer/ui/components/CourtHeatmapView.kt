package com.example.tennisscorer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.tracking.HomographyMapper

@Composable
fun CourtHeatmapView(
    heatmapBitmap: Bitmap?,
    bounceCount: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val lineColor = Color.White.copy(alpha = 0.55f)
            val strokeW = 1.dp.toPx()
            val netStrokeW = 1.8.dp.toPx()

            // Court boundaries
            drawLine(lineColor, Offset(0f, 0f), Offset(w, 0f), strokeW)
            drawLine(lineColor, Offset(0f, h), Offset(w, h), strokeW)
            drawLine(lineColor, Offset(0f, 0f), Offset(0f, h), strokeW)
            drawLine(lineColor, Offset(w, 0f), Offset(w, h), strokeW)

            // Net (middle)
            val netY = h / 2f
            drawLine(lineColor, Offset(0f, netY), Offset(w, netY), netStrokeW)

            // Service lines: 6.40m from each baseline
            val svcRatio = 6.40f / HomographyMapper.COURT_LENGTH_M
            val svcY1 = h * svcRatio
            val svcY2 = h * (1f - svcRatio)
            drawLine(lineColor, Offset(0f, svcY1), Offset(w, svcY1), strokeW)
            drawLine(lineColor, Offset(0f, svcY2), Offset(w, svcY2), strokeW)

            // Center service line (vertical, between service lines)
            drawLine(lineColor, Offset(w / 2f, svcY1), Offset(w / 2f, svcY2), strokeW)

            // Heatmap bitmap overlay
            heatmapBitmap?.let { bmp ->
                drawImage(
                    image = bmp.asImageBitmap(),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = 0.75f
                )
            }
        }

        if (bounceCount > 0) {
            Text(
                text = "$bounceCount bounces",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 3.dp, bottom = 2.dp)
            )
        }
    }
}
