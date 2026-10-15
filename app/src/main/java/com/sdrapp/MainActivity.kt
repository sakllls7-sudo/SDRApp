package com.sdrapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.sdrapp.sdr.MockSdrDevice
import com.sdrapp.sdr.RtlSdrDevice
import com.sdrapp.sdr.SdrController
import com.sdrapp.sdr.SettingsRepository
import com.sdrapp.ui.SDRApplication

class MainActivity : ComponentActivity() {

    private lateinit var controller: SdrController
    private lateinit var usbManager: UsbManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    @Suppress("DEPRECATION")
                    val device =
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) openRtl(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> controller.closeDevice()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        controller = SdrController(this, SettingsRepository(this))

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Auto-open an already-authorized dongle.
        val existing = findRtlDevice()
        if (existing != null && usbManager.hasPermission(existing)) openRtl(existing)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF1A1A2E),
                    surface = Color(0xFF16213E),
                    primary = Color(0xFF0F3460)
                )
            ) {
                SDRApplication(
                    controller = controller,
                    onConnectToggle = { toggleConnect() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {
        }
        controller.release()
        SDRCore.nativeCleanup()
    }

    private fun toggleConnect() {
        if (controller.state.value.connected) {
            controller.closeDevice()
        } else {
            requestConnect()
        }
    }

    private fun requestConnect() {
        val device = findRtlDevice()
        if (device == null) {
            // No dongle attached -> fall back to the built-in simulator.
            controller.openDevice(MockSdrDevice())
            return
        }
        if (usbManager.hasPermission(device)) {
            openRtl(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                this, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(packageName),
                flags
            )
            usbManager.requestPermission(device, pi)
        }
    }

    private fun openRtl(device: UsbDevice) {
        controller.openDevice(RtlSdrDevice(usbManager, device))
    }

    private fun findRtlDevice(): UsbDevice? {
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == RTL2832U_VID &&
                (device.productId == RTL2832U_PID ||
                    device.productId == RTL2838U_PID ||
                    device.productId == RTL2840U_PID)
            ) {
                return device
            }
        }
        return null
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.sdrapp.USB_PERMISSION"
        const val RTL2832U_VID = 0x0bda
        const val RTL2832U_PID = 0x2832
        const val RTL2838U_PID = 0x2838
        const val RTL2840U_PID = 0x2840
    }
}
