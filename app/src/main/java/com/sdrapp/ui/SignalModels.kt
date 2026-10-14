package com.sdrapp.ui

data class SignalData(
    val frequency: Double,
    val strength: Float,
    val noiseFloor: Float = -80f
)

data class ReceiverSettings(
    val demodMode: DemodMode = DemodMode.WFM,
    val gain: Float = 50f,
    val bandwidth: Float = 12_500f,
    val squelch: Float = -70f,
    val agcEnabled: Boolean = true,
    val noiseBlanker: Boolean = false
)

data class SourceDevice(
    val name: String,
    val type: String,
    val isConnected: Boolean = true,
    val sampleRate: Double = 2_400_000.0
)
