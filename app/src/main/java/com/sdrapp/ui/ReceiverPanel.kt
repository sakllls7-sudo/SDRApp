package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DemodMode(val displayName: String, val icon: ImageVector) {
    WFM("WFM", Icons.Default.Radio),
    NFM("NFM", Icons.Default.CellTower),
    AM("AM", Icons.Default.Waves),
    USB("USB", Icons.Default.ArrowUpward),
    LSB("LSB", Icons.Default.ArrowDownward),
    CW("CW", Icons.Default.Mic),
    RAW("RAW", Icons.Default.GraphicEq)
}

@Composable
fun ReceiverPanel(
    modifier: Modifier = Modifier,
    demodMode: DemodMode,
    onDemodModeChange: (DemodMode) -> Unit,
    gain: Float,
    onGainChange: (Float) -> Unit,
    bandwidth: Float,
    onBandwidthChange: (Float) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurface)
            .padding(8.dp)
    ) {
        Text(
            "RECEIVER",
            color = Color(0xFF88AAFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(8.dp))

        Text("DEMODULATOR", color = Color(0xFF6688AA), fontSize = 10.sp)

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(DemodMode.entries) { mode ->
                DemodModeItem(
                    mode = mode,
                    isSelected = mode == demodMode,
                    onClick = { onDemodModeChange(mode) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Divider(color = Color(0xFF2A4A7F))

        Spacer(Modifier.height(8.dp))

        Text("GAIN: ${gain.toInt()}%", color = Color.White, fontSize = 11.sp)
        Slider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = SpectrumGreen,
                activeTrackColor = SpectrumGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Text("BANDWIDTH: ${formatFrequency(bandwidth.toDouble())}", color = Color.White, fontSize = 11.sp)
        Slider(
            value = bandwidth,
            onValueChange = onBandwidthChange,
            valueRange = 1_000f..300_000f,
            colors = SliderDefaults.colors(
                thumbColor = WaterfallBlue,
                activeTrackColor = WaterfallBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text("FILTERS", color = Color(0xFF6688AA), fontSize = 10.sp)

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChipComponent("AGC", true, {})
            FilterChipComponent("NFM", false, {})
        }

        Spacer(Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = WaterfallBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Squelch", color = Color.White, fontSize = 11.sp)
                }
                Text("-70 dB", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun DemodModeItem(
    mode: DemodMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF1A5AAA) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            mode.icon,
            null,
            tint = if (isSelected) SpectrumGreen else Color.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            mode.displayName,
            color = if (isSelected) Color.White else Color(0xFFAAAACC),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun FilterChipComponent(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) Color(0xFF1A5AAA) else Color(0xFF0F2040)
    val textColor = if (selected) Color.White else Color(0xFFAAAACC)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
