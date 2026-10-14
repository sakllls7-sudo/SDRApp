#include <jni.h>
#include <android/log.h>
#include "signal_path/signal_path.h"

#define LOG_TAG "SDRJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static SignalPath* g_signal_path = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeInit(JNIEnv* env, jobject thiz) {
    if (g_signal_path) return JNI_TRUE;
    g_signal_path = new SignalPath();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeConfigure(JNIEnv* env, jobject thiz,
    jstring device_serial, jint sample_rate, jlong center_freq,
    jint gain, jint ppm, jboolean rtl_agc, jint direct_sampling,
    jboolean bias_tee, jint mode, jfloat bandwidth,
    jboolean squelch, jfloat squelch_db, jint deemphasis_us,
    jboolean noise_reduction, jfloat volume) {

    if (!g_signal_path) return;

    SignalPathConfig cfg;
    if (device_serial) {
        const char* str = env->GetStringUTFChars(device_serial, nullptr);
        cfg.device_serial = str;
        env->ReleaseStringUTFChars(device_serial, str);
    }
    cfg.sample_rate = sample_rate;
    cfg.center_freq = center_freq;
    cfg.gain = gain;
    cfg.ppm = ppm;
    cfg.rtl_agc = rtl_agc;
    cfg.direct_sampling = direct_sampling;
    cfg.bias_tee = bias_tee;
    cfg.mode = static_cast<DemodMode>(mode);
    cfg.bandwidth = bandwidth;
    cfg.squelch = squelch;
    cfg.squelch_db = squelch_db;
    cfg.deemphasis_us = deemphasis_us;
    cfg.noise_reduction = noise_reduction;
    cfg.volume = volume;

    g_signal_path->configure(cfg);
}

JNIEXPORT jboolean JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeStart(JNIEnv* env, jobject thiz) {
    if (!g_signal_path) return JNI_FALSE;
    return g_signal_path->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeStop(JNIEnv* env, jobject thiz) {
    if (g_signal_path) g_signal_path->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeIsRunning(JNIEnv* env, jobject thiz) {
    return g_signal_path && g_signal_path->is_running() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetCenterFreq(JNIEnv* env, jobject thiz, jlong hz) {
    if (g_signal_path) g_signal_path->set_center_freq(hz);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetSampleRate(JNIEnv* env, jobject thiz, jint hz) {
    if (g_signal_path) g_signal_path->set_sample_rate(hz);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetMode(JNIEnv* env, jobject thiz, jint mode) {
    if (g_signal_path) g_signal_path->set_mode(static_cast<DemodMode>(mode));
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetBandwidth(JNIEnv* env, jobject thiz, jfloat bw) {
    if (g_signal_path) g_signal_path->set_bandwidth(bw);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetVolume(JNIEnv* env, jobject thiz, jfloat vol) {
    if (g_signal_path) g_signal_path->set_volume(vol);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetSquelch(JNIEnv* env, jobject thiz, jboolean enable, jfloat db) {
    if (g_signal_path) g_signal_path->set_squelch(enable, db);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetAGC(JNIEnv* env, jobject thiz, jboolean enable) {
    if (g_signal_path) g_signal_path->set_agc(enable);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetDirectSampling(JNIEnv* env, jobject thiz, jint mode) {
    if (g_signal_path) g_signal_path->set_direct_sampling(mode);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeSetBiasTee(JNIEnv* env, jobject thiz, jboolean enable) {
    if (g_signal_path) g_signal_path->set_bias_tee(enable);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeGetSpectrum(JNIEnv* env, jobject thiz, jfloatArray out_db, jint max_bins) {
    if (!g_signal_path) return;
    jsize len = env->GetArrayLength(out_db);
    int max = std::min(max_bins, len);
    std::vector<float> db(max);
    uint32_t fft_size = 0;
    double center = 0, rate = 0;
    g_signal_path->get_spectrum(db.data(), max, fft_size, center, rate);
    env->SetFloatArrayRegion(out_db, 0, max, db.data());
}

JNIEXPORT jlong JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeGetCenterFreq(JNIEnv* env, jobject thiz) {
    return g_signal_path ? g_signal_path->get_center_freq() : 0;
}

JNIEXPORT jint JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeGetSampleRate(JNIEnv* env, jobject thiz) {
    return g_signal_path ? g_signal_path->get_sample_rate() : 0;
}

JNIEXPORT jint JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeGetMode(JNIEnv* env, jobject thiz) {
    return g_signal_path ? static_cast<jint>(g_signal_path->get_mode()) : 0;
}

JNIEXPORT jfloat JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeGetVolume(JNIEnv* env, jobject thiz) {
    return g_signal_path ? g_signal_path->get_volume() : 0.0f;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeStartRecording(JNIEnv* env, jobject thiz, jstring path) {
    if (!g_signal_path) return;
    const char* str = env->GetStringUTFChars(path, nullptr);
    g_signal_path->start_recording(str);
    env->ReleaseStringUTFChars(path, str);
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeStopRecording(JNIEnv* env, jobject thiz) {
    if (g_signal_path) g_signal_path->stop_recording();
}

JNIEXPORT jboolean JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeIsRecording(JNIEnv* env, jobject thiz) {
    return g_signal_path && g_signal_path->is_recording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeListDevices(JNIEnv* env, jobject thiz) {
    auto devices = SourceManager::instance().list_rtl_devices();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(devices.size(), stringClass, nullptr);
    for (size_t i = 0; i < devices.size(); ++i) {
        jstring jstr = env->NewStringUTF(devices[i].c_str());
        env->SetObjectArrayElement(result, i, jstr);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_SDRCore_nativeCleanup(JNIEnv* env, jobject thiz) {
    if (g_signal_path) {
        g_signal_path->stop();
        delete g_signal_path;
        g_signal_path = nullptr;
    }
}

}