#include "signal_path.h"
#include "source.h"
#include "demod.h"
#include "sink.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <cstdio>

#define LOG_TAG "SignalPath"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

SignalPath::SignalPath()
    : iq_ring_(config_.iq_ring_capacity)
    , audio_ring_(config_.audio_ring_capacity)
    , iq_packer_(iq_ring_, 4096)
    , audio_packer_(audio_ring_, config_.audio_block_size) {
}

SignalPath::~SignalPath() {
    stop();
    if (record_file_) {
        fclose(record_file_);
    }
}

bool SignalPath::configure(const SignalPathConfig& config) {
    std::lock_guard<std::mutex> lock(spectrum_mutex_);
    config_ = config;
    return apply_config();
}

bool SignalPath::apply_config() {
    if (!source_) {
        source_ = SourceManager::instance().create_java_source();
    }
    if (!demod_) {
        demod_ = std::make_unique<Demodulator>();
    }
    if (!sink_) {
        sink_ = SinkManager::instance().create_audio_sink();
    }

    SourceConfig scfg;
    scfg.sample_rate = config_.sample_rate;
    scfg.center_freq = config_.center_freq;
    scfg.gain = config_.gain;
    scfg.ppm = config_.ppm;
    scfg.rtl_agc = config_.rtl_agc;
    scfg.direct_sampling = config_.direct_sampling;
    scfg.bias_tee = config_.bias_tee;
    if (!source_->open(scfg)) {
        LOGE("Source open failed");
        return false;
    }

    DemodConfig dcfg;
    dcfg.mode = config_.mode;
    dcfg.input_rate = config_.sample_rate;
    dcfg.output_rate = 48000;
    dcfg.bandwidth = config_.bandwidth;
    dcfg.squelch = config_.squelch;
    dcfg.squelch_db = config_.squelch_db;
    dcfg.deemphasis_us = config_.deemphasis_us;
    dcfg.noise_reduction = config_.noise_reduction;
    demod_->configure(dcfg);

    SinkConfig sink_cfg;
    sink_cfg.sample_rate = 48000;
    sink_cfg.buffer_frames = 800;
    sink_cfg.volume = config_.volume;
    if (!sink_->open(sink_cfg)) {
        LOGE("Sink open failed");
        return false;
    }
    sink_->set_volume(config_.volume);

    return true;
}

bool SignalPath::start() {
    if (running_.load()) return true;
    if (!source_ || !demod_ || !sink_) {
        if (!apply_config()) return false;
    }

    iq_ring_.clear();
    audio_ring_.clear();
    iq_packer_.stop();
    audio_packer_.stop();

    running_.store(true);

    // Set up source callback to push IQ data to ring buffer
    source_->set_iq_callback([this](const int8_t* buf, size_t len) {
        iq_packer_.push(buf, len);
        total_iq_bytes_.fetch_add(len, std::memory_order_relaxed);
    });

    if (!source_->start()) {
        LOGE("Source start failed");
        running_.store(false);
        return false;
    }

    if (!sink_->start()) {
        LOGE("Sink start failed");
        source_->stop();
        running_.store(false);
        return false;
    }

    dsp_th_ = std::thread(&SignalPath::dsp_thread, this);
    audio_th_ = std::thread(&SignalPath::audio_thread, this);

    LOGI("SignalPath started");
    return true;
}

void SignalPath::stop() {
    if (!running_.load()) return;
    running_.store(false);

    if (source_) source_->stop();
    if (sink_) sink_->stop();

    if (dsp_th_.joinable()) dsp_th_.join();
    if (audio_th_.joinable()) audio_th_.join();

    iq_packer_.flush_partial();
    audio_packer_.flush_partial();

    if (record_file_) {
        std::lock_guard<std::mutex> lock(record_mutex_);
        fclose(record_file_);
        record_file_ = nullptr;
    }
    recording_.store(false);

    LOGI("SignalPath stopped");
}

void SignalPath::dsp_thread() {
    const size_t chunk_size = 4096;
    std::vector<int8_t> iq_chunk(chunk_size);
    std::vector<float> audio_chunk(4096);

    while (running_.load()) {
        size_t avail = iq_ring_.size();
        if (avail < chunk_size) {
            std::this_thread::sleep_for(std::chrono::microseconds(100));
            continue;
        }

        size_t read = iq_ring_.read(iq_chunk.data(), chunk_size);
        if (read == 0) continue;

        size_t out_count = demod_->process(
            reinterpret_cast<const uint8_t*>(iq_chunk.data()),
            read,
            audio_chunk.data()
        );

        if (out_count > 0) {
            if (recording_.load()) {
                std::lock_guard<std::mutex> lock(record_mutex_);
                if (record_file_) {
                    fwrite(iq_chunk.data(), 1, read, record_file_);
                }
            }

            audio_packer_.push(audio_chunk.data(), out_count);
            total_audio_samples_.fetch_add(out_count, std::memory_order_relaxed);
        }
    }
}

void SignalPath::audio_thread() {
    std::vector<float> audio_buf(config_.audio_block_size);

    while (running_.load()) {
        size_t avail = audio_ring_.size();
        if (avail < config_.audio_block_size) {
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            continue;
        }

        size_t read = audio_ring_.read(audio_buf.data(), config_.audio_block_size);
        if (read > 0) {
            sink_->push(audio_buf.data(), read);
        } else {
            underruns_.fetch_add(1, std::memory_order_relaxed);
        }
    }
}

void SignalPath::set_center_freq(uint64_t hz) {
    config_.center_freq = hz;
    if (source_) source_->set_center_freq(hz);
}

void SignalPath::set_sample_rate(uint32_t hz) {
    config_.sample_rate = hz;
    if (source_) source_->set_sample_rate(hz);
    if (demod_) {
        DemodConfig dcfg;
        dcfg.mode = config_.mode;
        dcfg.input_rate = hz;
        dcfg.output_rate = 48000;
        dcfg.bandwidth = config_.bandwidth;
        dcfg.squelch = config_.squelch;
        dcfg.squelch_db = config_.squelch_db;
        dcfg.deemphasis_us = config_.deemphasis_us;
        dcfg.noise_reduction = config_.noise_reduction;
        demod_->configure(dcfg);
    }
}

void SignalPath::set_mode(DemodMode mode) {
    config_.mode = mode;
    if (demod_) demod_->set_mode(mode);
}

void SignalPath::set_bandwidth(float bw) {
    config_.bandwidth = bw;
    if (demod_) demod_->set_bandwidth(bw);
}

void SignalPath::set_volume(float vol) {
    config_.volume = vol;
    if (sink_) sink_->set_volume(vol);
}

void SignalPath::set_squelch(bool enable, float db) {
    config_.squelch = enable;
    config_.squelch_db = db;
    if (demod_) demod_->set_squelch(enable, db);
}

void SignalPath::set_agc(bool enable) {
    config_.rtl_agc = enable;
    if (source_) source_->set_agc(enable);
}

void SignalPath::set_direct_sampling(int mode) {
    config_.direct_sampling = mode;
    if (source_) source_->set_direct_sampling(mode);
}

void SignalPath::set_bias_tee(bool enable) {
    config_.bias_tee = enable;
    if (source_) source_->set_bias_tee(enable);
}

void SignalPath::get_spectrum(float* out_db, size_t max_bins, uint32_t& out_fft_size, double& out_center_hz, double& out_rate_hz) {
    std::lock_guard<std::mutex> lock(spectrum_mutex_);
    size_t copy_bins = std::min(max_bins, spectrum_db_.size());
    if (copy_bins > 0) {
        std::copy(spectrum_db_.begin(), spectrum_db_.begin() + copy_bins, out_db);
    }
    out_fft_size = spectrum_fft_size_;
    out_center_hz = spectrum_center_hz_;
    out_rate_hz = spectrum_rate_hz_;
}

void SignalPath::start_recording(const char* path) {
    std::lock_guard<std::mutex> lock(record_mutex_);
    if (record_file_) return;
    record_file_ = fopen(path, "wb");
    if (record_file_) {
        recording_.store(true);
        LOGI("Recording started: %s", path);
    }
}

void SignalPath::stop_recording() {
    std::lock_guard<std::mutex> lock(record_mutex_);
    if (record_file_) {
        fclose(record_file_);
        record_file_ = nullptr;
        recording_.store(false);
        LOGI("Recording stopped");
    }
}

void SignalPath::push_iq(const int8_t* data, size_t len) {
    if (running_.load()) {
        iq_packer_.push(data, len);
        total_iq_bytes_.fetch_add(len, std::memory_order_relaxed);
    }
}