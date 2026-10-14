package com.sdrapp.sdr.native

class SDRCore {
    companion object {
        init {
            System.loadLibrary("sdrapp_core")
        }
    }

    external fun nativeInit(): Boolean
    external fun nativeConfigure(
        deviceSerial: String?,
        sampleRate: Int,
        centerFreq: Long,
        gain: Int,
        ppm: Int,
        rtlAgc: Boolean,
        directSampling: Int,
        biasTee: Boolean,
        mode: Int,
        bandwidth: Float,
        squelch: Boolean,
        squelchDb: Float,
        deemphasisUs: Int,
        noiseReduction: Boolean,
        volume: Float
    )
    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeIsRunning(): Boolean

    external fun nativeSetCenterFreq(hz: Long)
    external fun nativeSetSampleRate(hz: Int)
    external fun nativeSetMode(mode: Int)
    external fun nativeSetBandwidth(bw: Float)
    external fun nativeSetVolume(vol: Float)
    external fun nativeSetSquelch(enable: Boolean, db: Float)
    external fun nativeSetAGC(enable: Boolean)
    external fun nativeSetDirectSampling(mode: Int)
    external fun nativeSetBiasTee(enable: Boolean)

    external fun nativeGetSpectrum(outDb: FloatArray, maxBins: Int)
    external fun nativeGetCenterFreq(): Long
    external fun nativeGetSampleRate(): Int
    external fun nativeGetMode(): Int
    external fun nativeGetVolume(): Float

    external fun nativeStartRecording(path: String)
    external fun nativeStopRecording()
    external fun nativeIsRecording(): Boolean

    external fun nativeListDevices(): Array<String>
    external fun nativeCleanup()
}