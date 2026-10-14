#include "source.h"
#include <android/log.h>
#include <librtlsdr.h>
#include <vector>
#include <string>
#include <atomic>
#include <thread>
#include <cstring>

#define LOG_TAG "RTLSource"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class RTLSource : public ISource {
public:
    RTLSource() : dev_(nullptr), running_(false) {}
    ~RTLSource() { close(); }

    bool open(const SourceConfig& config) override {
        if (dev_) return true;

        int device_count = rtlsdr_get_device_count();
        if (device_count <= 0) {
            LOGE("No RTL-SDR devices found");
            return false;
        }

        int device_index = 0;
        if (!config.device_serial.empty()) {
            for (int i = 0; i < device_count; ++i) {
                char manufact[256], product[256], serial[256];
                rtlsdr_get_device_usb_strings(i, manufact, product, serial);
                if (config.device_serial == serial) {
                    device_index = i;
                    break;
                }
            }
        }

        int r = rtlsdr_open(&dev_, device_index);
        if (r < 0) {
            LOGE("rtlsdr_open failed: %d", r);
            return false;
        }

        cfg_ = config;
        configure_device();
        return true;
    }

    void close() override {
        stop();
        if (dev_) {
            rtlsdr_close(dev_);
            dev_ = nullptr;
        }
    }

    bool start() override {
        if (!dev_ || running_.load()) return false;
        running_.store(true);
        reader_thread_ = std::thread(&RTLSource::reader_loop, this);
        return true;
    }

    void stop() override {
        running_.store(false);
        if (reader_thread_.joinable()) reader_thread_.join();
        if (dev_) rtlsdr_cancel_async(dev_);
    }

    bool is_running() const override { return running_.load(); }

    std::string get_name() const override { return "RTL-SDR"; }

    std::vector<std::string> list_devices() override {
        std::vector<std::string> devices;
        int count = rtlsdr_get_device_count();
        for (int i = 0; i < count; ++i) {
            char manufact[256], product[256], serial[256];
            rtlsdr_get_device_usb_strings(i, manufact, product, serial);
            devices.push_back(std::string(manufact) + " " + product + " (" + serial + ")");
        }
        return devices;
    }

    uint32_t get_sample_rate() const override { return cfg_.sample_rate; }
    uint64_t get_center_freq() const override { return cfg_.center_freq; }

    bool set_center_freq(uint64_t hz) override {
        if (!dev_) return false;
        int r = rtlsdr_set_center_freq(dev_, hz);
        if (r == 0) cfg_.center_freq = hz;
        return r == 0;
    }

    bool set_sample_rate(uint32_t hz) override {
        if (!dev_) return false;
        int r = rtlsdr_set_sample_rate(dev_, hz);
        if (r == 0) cfg_.sample_rate = hz;
        return r == 0;
    }

    bool set_gain(int gain) override {
        if (!dev_) return false;
        int r = (gain < 0) ? rtlsdr_set_tuner_gain_mode(dev_, 1) : rtlsdr_set_tuner_gain(dev_, gain);
        if (r == 0) cfg_.gain = gain;
        return r == 0;
    }

    bool set_agc(bool enable) override {
        if (!dev_) return false;
        int r = rtlsdr_set_agc_mode(dev_, enable ? 1 : 0);
        if (r == 0) cfg_.rtl_agc = enable;
        return r == 0;
    }

    bool set_direct_sampling(int mode) override {
        if (!dev_) return false;
        int r = rtlsdr_set_direct_sampling(dev_, mode);
        if (r == 0) cfg_.direct_sampling = mode;
        return r == 0;
    }

    bool set_bias_tee(bool enable) override {
        if (!dev_) return false;
        int r = rtlsdr_set_bias_tee(dev_, enable ? 1 : 0);
        if (r == 0) cfg_.bias_tee = enable;
        return r == 0;
    }

    // Callback for async reading
    static void rtlsdr_callback(unsigned char* buf, uint32_t len, void* ctx) {
        RTLSource* self = static_cast<RTLSource*>(ctx);
        if (self->running_.load() && self->iq_callback_) {
            self->iq_callback_(buf, len);
        }
    }

    void set_iq_callback(std::function<void(const unsigned char*, uint32_t)> cb) {
        iq_callback_ = std::move(cb);
    }

private:
    void configure_device() {
        rtlsdr_set_sample_rate(dev_, cfg_.sample_rate);
        rtlsdr_set_center_freq(dev_, cfg_.center_freq);
        rtlsdr_set_freq_correction(dev_, cfg_.ppm);
        if (cfg_.gain < 0) rtlsdr_set_tuner_gain_mode(dev_, 1);
        else rtlsdr_set_tuner_gain(dev_, cfg_.gain);
        rtlsdr_set_agc_mode(dev_, cfg_.rtl_agc ? 1 : 0);
        rtlsdr_set_direct_sampling(dev_, cfg_.direct_sampling);
        rtlsdr_set_bias_tee(dev_, cfg_.bias_tee ? 1 : 0);
        rtlsdr_reset_buffer(dev_);
    }

    void reader_loop() {
        rtlsdr_read_async(dev_, rtlsdr_callback, this, 0, 0);
    }

    rtlsdr_dev_t* dev_ = nullptr;
    SourceConfig cfg_;
    std::atomic<bool> running_{false};
    std::thread reader_thread_;
    std::function<void(const unsigned char*, uint32_t)> iq_callback_;
};

std::unique_ptr<ISource> SourceManager::create_rtl_source() {
    return std::make_unique<RTLSource>();
}

std::vector<std::string> SourceManager::list_rtl_devices() {
    std::vector<std::string> devices;
    int count = rtlsdr_get_device_count();
    for (int i = 0; i < count; ++i) {
        char manufact[256], product[256], serial[256];
        rtlsdr_get_device_usb_strings(i, manufact, product, serial);
        devices.push_back(std::string(manufact) + " " + product + " (" + serial + ")");
    }
    return devices;
}