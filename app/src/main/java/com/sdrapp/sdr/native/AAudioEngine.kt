package com.sdrapp.sdr.native

class AAudioEngine {
    companion object {
        init {
            System.loadLibrary("sdrapp_audio")
        }
    }

    external fun nativeInit(sampleRate: Int): Boolean
    external fun nativeWrite(samples: FloatArray, count: Int)
    external fun nativeSetVolume(volume: Float)
    external fun nativeStop()
}
