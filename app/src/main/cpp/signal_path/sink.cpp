#include "sink.h"
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <atomic>
#include <thread>
#include <vector>
#include <cmath>

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

        result = AAudioStreamBuilder_openStream(builder, &stream_);
        AAudioStreamBuilder_delete(builder);
        if (result != AAUDIO_OK) {
            LOGE("AAudioStreamBuilder_openStream failed: %d", result);
            return false;
        }

        LOGI("AAudio stream opened: rate=%d, frames=%d", cfg_.sample_rate, cfg_.buffer_frames);
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
        writer_thread_ = std::thread(&AAudioSink::writer_loop, this);
        return true;
    }

    void stop() override {
        running_.store(false);
        if (writer_thread_.joinable()) writer_thread_.join();
        if (stream_) AAudioStream_requestStop(stream_);
    }

    bool is_running() const override { return running_.load(); }

    void set_volume(float volume) override {
        volume_ = std::clamp(volume, 0.0f, 1.0f);
    }

    float get_volume() const override { return volume_; }

    void push(const float* data, size_t count) override {
        if (!running_.load() || count == 0) return;
        // Non-blocking write with timeout
        aaudio_result_t result = AAudioStream_write(stream_, data, count, 100000000); // 100ms timeout
        if (result < 0) {
            LOGE("AAudioStream_write error: %d", result);
        }
    }

    size_t available() const override {
        if (!stream_) return 0;
        return AAudioStream_getBufferCapacity(stream_) - AAudioStream_getFramesAvailable(stream_);
    }

private:
    void writer_loop() {
        // This is a stub - actual writing happens via push() from DSP thread
        while (running_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }

    AAudioStream* stream_ = nullptr;
    SinkConfig cfg_;
    std::atomic<bool> running_{false};
    float volume_ = 0.8f;
    std::thread writer_thread_;
};

std::unique_ptr<ISink> SinkManager::create_audio_sink() {
    return std::make_unique<AAudioSink>();
}