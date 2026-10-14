package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun formatFrequency(freq: Double): String {
    return when {
        freq >= 1_000_000_000 -> String.format("%.6f GHz", freq / 1_000_000_000)
        freq >= 1_000_000 -> String.format("%.3f MHz", freq / 1_000_000)
        freq >= 1_000 -> String.format("%.1f kHz", freq / 1_000)
        else -> String.format("%.0f Hz", freq)
    }
}

@Composable
fun FrequencyDisplay(
    frequency: Double,
    onFrequencyChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0F3460), Color(0xFF1A5AAA), Color(0xFF0F3460))
                )
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatFrequency(frequency),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}
