#include "source.h"
#include <android/log.h>
#include <atomic>
#include <thread>

#define LOG_TAG "JavaSource"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class JavaSource : public ISource {
public:
    JavaSource() : running_(false), sample_rate_(2400000), center_freq_(100000000) {}
    ~JavaSource() { close(); }

    bool open(const SourceConfig& config) override {
        cfg_ = config;
        sample_rate_ = config.sample_rate;
        center_freq_ = config.center_freq;
        return true;
    }

    void close() override {
        stop();
    }

    bool start() override {
        if (running_.load()) return true;
        running_.store(true);
        LOGI("JavaSource started");
        return true;
    }

    void stop() override {
        running_.store(false);
        LOGI("JavaSource stopped");
    }

    bool is_running() const override { return running_.load(); }

    std::string get_name() const override { return "RTL-SDR (Java)"; }

    uint32_t get_sample_rate() const override { return sample_rate_; }
    uint64_t get_center_freq() const override { return center_freq_; }

    bool set_center_freq(uint64_t hz) override {
        center_freq_ = hz;
        return true;
    }

    bool set_sample_rate(uint32_t hz) override {
        sample_rate_ = hz;
        return true;
    }

    bool set_gain(int gain) override { return true; }
    bool set_agc(bool enable) override { return true; }
    bool set_direct_sampling(int mode) override { return true; }
    bool set_bias_tee(bool enable) override { return true; }

    void push_iq(const int8_t* data, size_t len) override {
        if (running_.load() && iq_callback_) {
            iq_callback_(data, len);
}

std::unique_ptr<ISource> SourceManager::create_java_source() {
    return std::make_unique<JavaSource>();
}
    }

    void set_iq_callback(std::function<void(const int8_t*, size_t)> cb) {
        iq_callback_ = std::move(cb);
    }

private:
    SourceConfig cfg_;
    std::atomic<bool> running_{false};
    uint32_t sample_rate_;
    uint64_t center_freq_;
    std::function<void(const int8_t*, size_t)> iq_callback_;
};

std::unique_ptr<ISource> SourceManager::create_java_source() {
    return std::make_unique<JavaSource>();
}