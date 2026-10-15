#include "sink.h"
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <atomic>
#include <thread>
#include <vector>
#include <cmath>
#include <algorithm>
#include <mutex>
#include "buffer/buffer.h"

#define LOG_TAG "AAudioSink"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class AAudioSink : public ISink {
public:
    AAudioSink() : stream_(nullptr), running_(false), volume_(0.8f) {}
    ~AAudioSink() { close(); }

    bool open(const SinkConfig& config) override {
        if (stream_) return true;
        cfg_ = config;

        AAudioStreamBuilder* builder;
        aaudio_result_t result = AAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK) {
            LOGE("AAudio_createStreamBuilder failed: %d", result);
            return false;
        }

        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setSampleRate(builder, cfg_.sample_rate);
        AAudioStreamBuilder_setChannelCount(builder, 1); // mono
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setBufferCapacityInFrames(builder, cfg_.buffer_frames);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
        AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);

        result = AAudioStreamBuilder_openStream(builder, &stream_);
        AAudioStreamBuilder_delete(builder);
        if (result != AAUDIO_OK) {
            LOGE("AAudioStreamBuilder_openStream failed: %d", result);
            return false;
        }

        LOGI("AAudio stream opened: rate=%d, frames=%d, callback mode",
             cfg_.sample_rate, cfg_.buffer_frames);
        return true;
    }

    void close() override {
        stop();
        if (stream_) {
            AAudioStream_close(stream_);
            stream_ = nullptr;
        }
    }

    bool start() override {
        if (!stream_ || running_.load()) return false;
        aaudio_result_t result = AAudioStream_requestStart(stream_);
        if (result != AAUDIO_OK) {
            LOGE("AAudioStream_requestStart failed: %d", result);
            return false;
        }
        running_.store(true);
        LOGI("AAudio sink started (callback mode)");
        return true;
    }

    void stop() override {
        running_.store(false);
        if (stream_) AAudioStream_requestStop(stream_);
    }

    bool is_running() const override { return running_.load(); }

    void set_volume(float volume) override {
        volume_ = std::clamp(volume, 0.0f, 1.0f);
    }

    float get_volume() const override { return volume_; }

    void push(const float* data, size_t count) override {
        if (!running_.load() || count == 0) return;

        // Lock-free ring buffer push - non-blocking
        std::lock_guard<std::mutex> lock(buf_mutex_);
        size_t written = ring_.write(data, count);
        if (written < count) {
            underruns_.fetch_add(1, std::memory_order_relaxed);
            // If buffer full, we drop samples (callback will output silence)
        }
    }

    size_t available() const override {
        if (!stream_) return 0;
        int32_t capacity = AAudioStream_getBufferCapacityInFrames(stream_);
        int32_t written = AAudioStream_getFramesWritten(stream_);
        int32_t read = AAudioStream_getFramesRead(stream_);
        int32_t available = capacity - (written - read);
        return static_cast<size_t>(std::max(0, available));
    }

    size_t get_underruns() const { return underruns_.load(); }

    // Static callback for AAudio
    static aaudio_data_callback_result dataCallback(
        AAudioStream* stream, void* userData, void* audioData, int32_t numFrames) {

        AAudioSink* self = static_cast<AAudioSink*>(userData);
        float* out = static_cast<float*>(audioData);

        float vol = self->volume_.load(std::memory_order_relaxed);

        size_t read = 0;
        {
            std::lock_guard<std::mutex> lock(self->buf_mutex_);
            read = self->ring_.read(out, numFrames);
        }

        if (read < static_cast<size_t>(numFrames)) {
            // Fill remainder with silence
            std::fill(out + read, out + numFrames, 0.0f);
            self->underruns_.fetch_add(1, std::memory_order_relaxed);
        }

        // Apply volume
        if (vol != 1.0f) {
            for (int i = 0; i < numFrames; ++i) {
                out[i] *= vol;
            }
        }

        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static void errorCallback(AAudioStream* stream, void* userData, aaudio_result_t error) {
        if (error == AAUDIO_ERROR_DISCONNECTED) {
            LOGI("AAudio device disconnected");
        } else {
            LOGE("AAudio error: %d", error);
        }
    }

private:
    AAudioStream* stream_ = nullptr;
    SinkConfig cfg_;
    std::atomic<bool> running_{false};
    std::atomic<float> volume_{0.8f};

    // Ring buffer for audio data (DSP thread -> audio callback)
    // 4 seconds at 48kHz = 192000 frames, enough for 2+ seconds of buffering
    RingBuffer<float> ring_{48000 * 4};
    mutable std::mutex buf_mutex_;

    std::atomic<uint64_t> underruns_{0};
};

std::unique_ptr<ISink> SinkManager::create_audio_sink() {
    return std::make_unique<AAudioSink>();
}