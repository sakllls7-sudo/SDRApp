package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBar(
    frequency: Double,
    isPlaying: Boolean,
    isConnected: Boolean,
    isRecording: Boolean,
    onFrequencyChange: (Double) -> Unit,
    onPlayToggle: () -> Unit,
    onConnectToggle: () -> Unit,
    onRecordToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(DarkSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = Color.White)
        }

        Spacer(Modifier.width(8.dp))

        Text(
            "SDR++",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(16.dp))

        IconButton(onClick = onConnectToggle) {
            Icon(
                Icons.Default.Usb,
                "Connect",
                tint = if (isConnected) SpectrumGreen else Color.Gray
            )
        }

        IconButton(onClick = onPlayToggle, enabled = isConnected) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                if (isPlaying) "Stop" else "Play",
                tint = when {
                    !isConnected -> Color.DarkGray
                    isPlaying -> Color.Red
                    else -> SpectrumGreen
                }
            )
        }

        IconButton(onClick = onRecordToggle, enabled = isConnected) {
            Icon(
                Icons.Default.FiberManualRecord,
                "Record IQ",
                tint = when {
                    !isConnected -> Color.DarkGray
                    isRecording -> Color.Red
                    else -> Color(0xFFCC3333).copy(alpha = 0.6f)
                }
            )
        }

        Spacer(Modifier.weight(1f))

        FrequencyPad(
            frequency = frequency,
            onFrequencyChange = onFrequencyChange
        )
    }
}
