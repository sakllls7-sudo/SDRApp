package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val AccentBlue = Color(0xFF0F3460)
val SpectrumGreen = Color(0xFF00FF88)
val WaterfallBlue = Color(0xFF0088FF)

@Composable
fun SDRApplication() {
    var frequency by remember { mutableStateOf(100_000_000.0) }
    var demodMode by remember { mutableStateOf(DemodMode.WFM) }
    var gain by remember { mutableFloatStateOf(50f) }
    var bandwidth by remember { mutableFloatStateOf(12_500f) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedBand by remember { mutableStateOf(Band.FM_BROADCAST) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                frequency = frequency,
                isPlaying = isPlaying,
                onFrequencyChange = { frequency = it },
                onPlayToggle = { isPlaying = !isPlaying }
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                SourcePanel(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    selectedBand = selectedBand,
                    onBandSelect = { selectedBand = it }
                )

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    ) {
                        SpectrumView(
                            frequency = frequency,
                            isPlaying = isPlaying
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        WaterfallView(
                            frequency = frequency,
                            isPlaying = isPlaying
                        )
                    }

                    FrequencyDisplay(
                        frequency = frequency,
                        onFrequencyChange = { frequency = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                ReceiverPanel(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                    demodMode = demodMode,
                    onDemodModeChange = { demodMode = it },
                    gain = gain,
                    onGainChange = { gain = it },
                    bandwidth = bandwidth,
                    onBandwidthChange = { bandwidth = it }
                )
            }
        }
    }
}
