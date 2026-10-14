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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Band(val displayName: String, val freqStart: Double, val freqEnd: Double, val icon: ImageVector) {
    AIRBAND("Авиадиапазон", 108_000_000.0, 137_000_000.0, Icons.Default.Flight),
    VHF("УКВ (VHF)", 137_000_000.0, 174_000_000.0, Icons.Default.Waves),
    UHF("ДМВ (UHF)", 400_000_000.0, 512_000_000.0, Icons.Default.Router),
    FM_BROADCAST("FM-радио", 87_500_000.0, 108_000_000.0, Icons.Default.Radio),
    AM_BROADCAST("AM-радио", 530_000.0, 1_700_000.0, Icons.Default.Radio),
    SHORTWAVE("Короткие волны", 2_300_000.0, 30_000_000.0, Icons.Default.Public),
    CB("CB-радио", 26_965_000.0, 27_405_000.0, Icons.Default.Chat),
    ADSB("ADS-B", 1_090_000_000.0, 1_090_000_000.0, Icons.Default.FlightTakeoff),
    AIS("AIS", 161_975_000.0, 162_025_000.0, Icons.Default.DirectionsBoat),
    WEATHER("Метеоспутник", 137_000_000.0, 138_000_000.0, Icons.Default.Cloud),
    GSM("GSM", 900_000_000.0, 960_000_000.0, Icons.Default.PhoneAndroid),
    LTE("LTE", 700_000_000.0, 2_700_000_000.0, Icons.Default.SignalCellular4Bar),
    AIRBAND_RX("Авиадиапазон RX", 108_000_000.0, 137_000_000.0, Icons.Default.FlightLand)
}

@Composable
fun SourcePanel(
    modifier: Modifier = Modifier,
    selectedBand: Band,
    onBandSelect: (Band) -> Unit,
    deviceName: String = "RTL-SDR",
    isConnected: Boolean = false,
    onConnectToggle: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurface)
            .padding(8.dp)
    ) {
        Text(
            "ИСТОЧНИК",
            color = Color(0xFF88AAFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AccentBlue)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsInputAntenna, null, tint = SpectrumGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(deviceName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("RTL2832U", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onConnectToggle,
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color(0xFF8B1A1A) else Color(0xFF1A5AAA)
                    )
                ) {
                    Text(
                        if (isConnected) "Отключить" else "Подключить",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("ДИАПАЗОНЫ", color = Color(0xFF88AAFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(Band.entries) { band ->
                BandItem(
                    band = band,
                    isSelected = band == selectedBand,
                    onClick = { onBandSelect(band) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0F3460))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Usb, null,
                tint = if (isConnected) SpectrumGreen else Color.Gray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isConnected) "Подключено" else "Отключено",
                color = if (isConnected) SpectrumGreen else Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun BandItem(
    band: Band,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF1A5AAA) else Color.Transparent
    val textColor = if (isSelected) Color.White else Color(0xFFAAAACC)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            band.icon,
            null,
            tint = if (isSelected) SpectrumGreen else Color.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(band.displayName, color = textColor, fontSize = 11.sp)
    }
}
