package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBar(
    frequency: Double,
    isPlaying: Boolean,
    onFrequencyChange: (Double) -> Unit,
    onPlayToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(DarkSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.Default.Menu, "Menu", tint = Color.White)
        }

        Spacer(Modifier.width(8.dp))

        Text(
            "SDR++",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(16.dp))

        IconButton(onClick = onPlayToggle) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                if (isPlaying) "Stop" else "Play",
                tint = if (isPlaying) Color.Red else SpectrumGreen
            )
        }

        Spacer(Modifier.weight(1f))

        FrequencyPad(
            frequency = frequency,
            onFrequencyChange = onFrequencyChange
        )
    }
}
