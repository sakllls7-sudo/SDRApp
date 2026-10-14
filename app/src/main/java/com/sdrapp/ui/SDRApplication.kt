package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sdrapp.sdr.DemodType
import com.sdrapp.sdr.SdrController

val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val AccentBlue = Color(0xFF0F3460)
val SpectrumGreen = Color(0xFF00FF88)
val WaterfallBlue = Color(0xFF0088FF)

private fun DemodMode.toDemodType(): DemodType = DemodType.valueOf(name)
private fun DemodType.toDemodMode(): DemodMode = DemodMode.valueOf(name)

@Composable
fun SDRApplication(
    controller: SdrController,
    onConnectToggle: () -> Unit
) {
    val state by controller.state.collectAsState()

    var selectedBand by remember { mutableStateOf(Band.FM_BROADCAST) }
    var spanHz by remember { mutableDoubleStateOf(state.sampleRate.toDouble()) }

    fun tune(deltaHz: Double) {
        controller.setFrequency((controller.state.value.frequency + deltaHz).coerceIn(24_000_000.0, 1_766_000_000.0))
    }

    fun zoom(scale: Float) {
        spanHz = (spanHz / scale).coerceIn(10_000.0, 20_000_000.0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                frequency = state.frequency,
                isPlaying = state.playing,
                isConnected = state.connected,
                isRecording = state.recording,
                onFrequencyChange = { controller.setFrequency(it) },
                onPlayToggle = {
                    if (state.playing) controller.stopPlayback() else controller.startPlayback()
                },
                onConnectToggle = onConnectToggle,
                onRecordToggle = { controller.toggleRecording() }
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
                    onBandSelect = {
                        selectedBand = it
                        controller.setFrequency((it.freqStart + it.freqEnd) / 2.0)
                    },
                    deviceName = state.deviceName,
                    isConnected = state.connected,
                    onConnectToggle = onConnectToggle
                )

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    ) {
                        SpectrumView(
                            frequency = state.frequency,
                            isPlaying = state.playing,
                            spanHz = spanHz,
                            onTune = { tune(it) },
                            onZoom = { zoom(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        WaterfallView(
                            frequency = state.frequency,
                            isPlaying = state.playing,
                            spanHz = spanHz,
                            onTune = { tune(it) },
                            onZoom = { zoom(it) }
                        )
                    }

                    FrequencyDisplay(
                        frequency = state.frequency,
                        onFrequencyChange = { controller.setFrequency(it) },
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
                    demodMode = state.demodType.toDemodMode(),
                    onDemodModeChange = { controller.setDemodType(it.toDemodType()) },
                    gain = state.gainPercent,
                    onGainChange = { controller.setGainPercent(it) },
                    bandwidth = state.bandwidthHz,
                    onBandwidthChange = { controller.setBandwidth(it) },
                    agcEnabled = state.agcEnabled,
                    onAgcChange = { controller.setAgc(it) },
                    signalDbm = state.signalDbm
                )
            }
        }
    }
}
