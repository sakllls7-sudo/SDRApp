#pragma once

#include <cstdint>
#include <atomic>
#include <thread>
#include <functional>
#include "buffer/buffer.h"

struct SinkConfig {
    uint32_t sample_rate = 48000;
    uint32_t buffer_frames = 800; // ~16ms at 48kHz
    float volume = 0.8f;
};

class ISink {
public:
    virtual ~ISink() = default;
    virtual bool open(const SinkConfig& config) = 0;
    virtual void close() = 0;
    virtual bool start() = 0;
    virtual void stop() = 0;
    virtual bool is_running() const = 0;
    virtual void set_volume(float volume) = 0;
    virtual float get_volume() const = 0;
    // Push float samples (mono, -1.0 to 1.0)
    virtual void push(const float* data, size_t count) = 0;
    virtual size_t available() const = 0;
};

class SinkManager {
public:
    static SinkManager& instance() {
        static SinkManager inst;
        return inst;
    }

    std::unique_ptr<ISink> create_audio_sink();

private:
    SinkManager() = default;
};