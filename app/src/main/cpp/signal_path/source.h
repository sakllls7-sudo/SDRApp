#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <atomic>
#include <thread>
#include "buffer/buffer.h"

struct SourceConfig {
    std::string device_serial;
    uint32_t sample_rate = 2400000;
    uint64_t center_freq = 100000000;
    int gain = -1; // -1 = auto
    int ppm = 0;
    bool rtl_agc = false;
    int direct_sampling = 0;
    bool bias_tee = false;
};

class ISource {
public:
    virtual ~ISource() = default;
    virtual bool open(const SourceConfig& config) = 0;
    virtual void close() = 0;
    virtual bool start() = 0;
    virtual void stop() = 0;
    virtual bool is_running() const = 0;
    virtual std::string get_name() const = 0;
    virtual std::vector<std::string> list_devices() = 0;
    virtual uint32_t get_sample_rate() const = 0;
    virtual uint64_t get_center_freq() const = 0;
    virtual bool set_center_freq(uint64_t hz) = 0;
    virtual bool set_sample_rate(uint32_t hz) = 0;
    virtual bool set_gain(int gain) = 0;
    virtual bool set_agc(bool enable) = 0;
    virtual bool set_direct_sampling(int mode) = 0;
    virtual bool set_bias_tee(bool enable) = 0;
};

class SourceManager {
public:
    static SourceManager& instance() {
        static SourceManager inst;
        return inst;
    }

    std::unique_ptr<ISource> create_rtl_source();
    std::vector<std::string> list_rtl_devices();

private:
    SourceManager() = default;
};