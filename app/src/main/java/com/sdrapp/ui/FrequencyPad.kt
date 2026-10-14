package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FrequencyPad(
    frequency: Double,
    onFrequencyChange: (Double) -> Unit
) {
    var editingDigit by remember { mutableIntStateOf(-1) }
    val freqStr = String.format("%012.0f", frequency)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentBlue)
            .border(1.dp, Color(0xFF2A4A7F), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        freqStr.forEachIndexed { index, char ->
            val digitValue = char.digitToIntOrNull()
            val multiplier = 11 - index

            Box(
                modifier = Modifier
                    .clickable {
                        if (digitValue != null && index > 2) {
                            editingDigit = if (editingDigit == index) -1 else index
                        }
                    }
                    .background(
                        if (editingDigit == index) Color(0xFF1A5AAA) else Color.Transparent,
                        RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 1.dp)
            ) {
                Text(
                    text = if (index == 3) "." else char.toString(),
                    color = if (index == 3) Color.Gray else {
                        if (index < 3) Color(0xFF88AAFF) else Color.White
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (digitValue != null && editingDigit == index) {
                val step = Math.pow(10.0, multiplier.toDouble()).toLong()
                Row {
                    IconButton(
                        onClick = {
                            onFrequencyChange(frequency + step)
                            editingDigit = -1
                        },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Text("▲", color = SpectrumGreen, fontSize = 10.sp)
                    }
                    IconButton(
                        onClick = {
                            onFrequencyChange(frequency - step)
                            editingDigit = -1
                        },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Text("▼", color = Color.Red, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
