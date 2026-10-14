#pragma once

#include "source.h"
#include "demod.h"
#include "sink.h"
#include "buffer/buffer.h"
#include <atomic>
#include <thread>
#include <memory>
#include <mutex>
#include <condition_variable>

struct SignalPathConfig {
    // Source
    std::string device_serial;
    uint32_t sample_rate = 2400000;
    uint64_t center_freq = 100000000;
    int gain = -1;
    int ppm = 0;
    bool rtl_agc = false;
    int direct_sampling = 0;
    bool bias_tee = false;

    // Demod
    DemodMode mode = DemodMode::WFM;
    float bandwidth = 12500.0f;
    bool squelch = false;
    float squelch_db = -70.0f;
    int deemphasis_us = 75;
    bool noise_reduction = false;

    // Sink
    float volume = 0.8f;

    // Internal
    size_t reader_buffer_size = 256 * 1024; // 256KB
    size_t iq_ring_capacity = 1024 * 1024; // 1M complex samples
    size_t audio_ring_capacity = 48000 * 4; // 4 seconds of audio
    size_t audio_block_size = 960; // 20ms at 48kHz
};

class SignalPath {
public:
    SignalPath();
    ~SignalPath();

    bool configure(const SignalPathConfig& config);
    bool apply_config();  // internal
    bool start();
    void stop();
    bool is_running() const { return running_.load(); }

    // Getters for UI
    uint32_t get_sample_rate() const { return config_.sample_rate; }
    uint64_t get_center_freq() const { return config_.center_freq; }
    DemodMode get_mode() const { return config_.mode; }
    float get_volume() const { return config_.volume; }

    // Setters (thread-safe)
    void set_center_freq(uint64_t hz);
    void set_sample_rate(uint32_t hz);
    void set_gain(int gain);
    void set_mode(DemodMode mode);
    void set_bandwidth(float bw);
    void set_volume(float vol);
    void set_squelch(bool enable, float db);
    void set_agc(bool enable);
    void set_direct_sampling(int mode);
    void set_bias_tee(bool enable);

    // Spectrum/Waterfall data
    // Call from UI thread to get latest FFT magnitude (dBFS)
    void get_spectrum(float* out_db, size_t max_bins, uint32_t& out_fft_size, double& out_center_hz, double& out_rate_hz);

    // For recorder
    bool is_recording() const { return recording_.load(); }
    void start_recording(const char* path);
    void stop_recording();

    // Push IQ data from Java
    void push_iq(const int8_t* data, size_t len);

private:

    SignalPathConfig config_;
    std::atomic<bool> running_{false};
    std::atomic<bool> recording_{false};

    // Components
    std::unique_ptr<ISource> source_;
    std::unique_ptr<Demodulator> demod_;
    std::unique_ptr<ISink> sink_;

    // Buffers
    RingBuffer<int8_t> iq_ring_;      // raw IQ from source
    RingBuffer<float> audio_ring_;    // demodulated audio
    Packer<int8_t> iq_packer_;
    Packer<float> audio_packer_;

    // Threads
    std::thread reader_th_;
    std::thread dsp_th_;
    std::thread audio_th_;

    // Spectrum
    std::vector<float> spectrum_db_;
    std::mutex spectrum_mutex_;
    uint32_t spectrum_fft_size_ = 8192;
    double spectrum_center_hz_ = 0;
    double spectrum_rate_hz_ = 0;

    // Recording
    FILE* record_file_ = nullptr;
    std::mutex record_mutex_;

    // Stats
    std::atomic<uint64_t> total_iq_bytes_{0};
    std::atomic<uint64_t> total_audio_samples_{0};
    std::atomic<int> underruns_{0};
};