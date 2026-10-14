package com.sdrapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdrapp.sdr.SdrController
import com.sdrapp.sdr.SdrState
import kotlin.math.roundToInt

private val SectionBlue = Color(0xFF88AAFF)
private val LabelGrey = Color(0xFF8899BB)

@Composable
fun SettingsScreen(
    state: SdrState,
    controller: SdrController,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(DarkSurface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SETTINGS",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Text("SDR++ style", color = LabelGrey, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { controller.resetSettings() }) {
                Text("RESET", color = Color(0xFFCC6666), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 4.dp)
            ) {
                Section("SOURCE / HARDWARE")

                ChoiceRow(
                    "Sample rate",
                    listOf(
                        250_000 to "250k", 1_024_000 to "1.02M", 1_200_000 to "1.2M",
                        1_536_000 to "1.53M", 2_048_000 to "2.04M", 2_400_000 to "2.4M",
                        3_200_000 to "3.2M"
                    ),
                    state.sampleRate
                ) { controller.setSampleRate(it) }

                IntSliderRow("PPM correction", state.ppmCorrection, -200, 200, 400) {
                    controller.setPpmCorrection(it)
                }

                SwitchRow("RTL AGC (sampler)", state.rtlAgc) { controller.setRtlAgc(it) }
                SwitchRow("Tuner AGC", state.agcEnabled) { controller.setAgc(it) }

                ChoiceRow(
                    "Direct sampling (HF)",
                    listOf(0 to "Off", 1 to "I-ADC", 2 to "Q-ADC"),
                    state.directSampling
                ) { controller.setDirectSampling(it) }

                SwitchRow("Bias-T (needs v3+ dongle)", state.biasTee) { controller.setBiasTee(it) }
                SwitchRow("Offset tuning (E4000 only)", state.offsetTuning) { controller.setOffsetTuning(it) }

                Section("DEMODULATION")

                SwitchRow("Squelch", state.squelchEnabled) {
                    controller.setSquelch(it, state.squelchDb)
                }
                SliderRow(
                    "Squelch level", state.squelchDb, -120f, 0f,
                    steps = 0, valueText = String.format("%.0f dB", state.squelchDb)
                ) { controller.setSquelch(state.squelchEnabled, it) }

                ChoiceRow(
                    "De-emphasis (WFM)",
                    listOf(0 to "Off", 50 to "50 us", 75 to "75 us"),
                    state.deemphasisUs
                ) { controller.setDeemphasis(it) }

                SwitchRow("Noise reduction", state.noiseReduction) { controller.setNoiseReduction(it) }

                Section("AUDIO")
                SliderRow(
                    "Volume", state.audioVolume, 0f, 100f,
                    valueText = "${state.audioVolume.roundToInt()}%"
                ) { controller.setAudioVolume(it) }

                Section("FFT / DISPLAY")
                ChoiceRow(
                    "FFT size",
                    listOf(1024 to "1k", 2048 to "2k", 4096 to "4k", 8192 to "8k", 16384 to "16k", 32768 to "32k"),
                    state.fftSize
                ) { controller.setDisplay(fftSize = it) }
                ChoiceRow(
                    "FFT window",
                    listOf("rectangular" to "Rect", "hann" to "Hann", "blackman" to "Blackman", "nuttall" to "Nuttall"),
                    state.fftWindow
                ) { controller.setDisplay(fftWindow = it) }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp)
            ) {
                IntSliderRow("FFT rate", state.fftRate, 5, 60, 55) {
                    controller.setDisplay(fftRate = it)
                }
                IntSliderRow("Waterfall speed (ms)", state.waterfallSpeed, 20, 200, 180) {
                    controller.setDisplay(waterfallSpeed = it)
                }
                IntSliderRow("Spectrum / waterfall split", state.splitRatio.roundToInt(), 20, 80, 60) {
                    controller.setDisplay(splitRatio = it.toFloat())
                }
                IntSliderRow("Averaging", state.averaging, 0, 100, 100) {
                    controller.setDisplay(averaging = it)
                }
                SliderRow(
                    "dB max", state.dbMax, -60f, 0f,
                    valueText = "${state.dbMax.roundToInt()} dB"
                ) { controller.setDisplay(dbMax = it) }
                SliderRow(
                    "dB min", state.dbMin, -140f, -40f,
                    valueText = "${state.dbMin.roundToInt()} dB"
                ) { controller.setDisplay(dbMin = it) }

                ChoiceRow(
                    "Waterfall palette",
                    PALETTE_NAMES.map { it to it.replaceFirstChar { c -> c.uppercase() } },
                    state.waterfallPalette
                ) { controller.setDisplay(waterfallPalette = it) }

                Section("TUNING")
                SwitchRow("Snap to step", state.snapToStep) {
                    controller.setTuning(it, state.stepHz)
                }
                ChoiceRow(
                    "Step size",
                    listOf(
                        1_000 to "1k", 5_000 to "5k", 6_250 to "6.25k", 12_500 to "12.5k",
                        25_000 to "25k", 50_000 to "50k", 100_000 to "100k"
                    ),
                    state.stepHz
                ) { controller.setTuning(state.snapToStep, it) }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        color = SectionBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(2.dp))
    Divider(color = Color(0xFF2A4A7F))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = SpectrumGreen)
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int = 0,
    valueText: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = LabelGrey, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(valueText, color = Color.White, fontSize = 11.sp)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = SpectrumGreen,
                activeTrackColor = SpectrumGreen
            )
        )
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    steps: Int,
    onChange: (Int) -> Unit
) {
    SliderRow(
        label = label,
        value = value.toFloat(),
        min = min.toFloat(),
        max = max.toFloat(),
        steps = (steps - 1).coerceAtLeast(0),
        valueText = value.toString()
    ) { onChange(it.roundToInt()) }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = LabelGrey, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color(0xFF1A5AAA) else Color(0xFF0F2040))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text,
                        color = if (isSel) Color.White else LabelGrey,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
