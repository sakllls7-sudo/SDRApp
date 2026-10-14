#include <aaudio/AAudio.h>
#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cmath>

#define LOG_TAG "AAudioWrapper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static AAudioStream *g_stream = nullptr;
static int32_t g_sampleRate = 48000;
static int32_t g_bufferSize = 800;
static float g_volume = 0.8f;
static bool g_running = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_sdrapp_sdr_native_AAudioEngine_nativeInit(JNIEnv *env, jobject thiz, jint sampleRate) {
    g_sampleRate = sampleRate;
    g_bufferSize = (int32_t)(sampleRate / 60.0);

    AAudioStreamBuilder *builder;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create AAudio builder: %d", result);
        return JNI_FALSE;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setSampleRate(builder, g_sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, g_bufferSize);
    AAudioStreamBuilder_setVolume(builder, g_volume);

    result = AAudioStreamBuilder_openStream(builder, &g_stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to open AAudio stream: %d", result);
        return JNI_FALSE;
    }

    result = AAudioStream_requestStart(g_stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream: %d", result);
        return JNI_FALSE;
    }

    g_running = true;
    LOGI("AAudio stream started: rate=%d, buffer=%d, volume=%.2f", g_sampleRate, g_bufferSize, g_volume);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_AAudioEngine_nativeWrite(JNIEnv *env, jobject thiz, jfloatArray samples, jint count) {
    if (!g_stream || !g_running || count <= 0) return;

    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    if (!data) return;

    // Apply volume scaling
    if (g_volume != 1.0f) {
        for (int i = 0; i < count; i++) {
            data[i] *= g_volume;
        }
    }

    aaudio_result_t result = AAudioStream_write(g_stream, data, (int32_t)count, 100000000);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);

    if (result < 0) {
        LOGE("AAudio write error: %d", result);
    }
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_AAudioEngine_nativeSetVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    g_volume = volume;
}

JNIEXPORT void JNICALL
Java_com_sdrapp_sdr_native_AAudioEngine_nativeStop(JNIEnv *env, jobject thiz) {
    g_running = false;
    if (g_stream) {
        AAudioStream_requestStop(g_stream);
        AAudioStream_close(g_stream);
        g_stream = nullptr;
        LOGI("AAudio stream stopped");
    }
}

}