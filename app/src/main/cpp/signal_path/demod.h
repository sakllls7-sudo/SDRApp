#pragma once

#include <cstdint>
#include <cmath>
#include <atomic>
#include <vector>

enum class DemodMode {
    WFM, NFM, AM, USB, LSB, CW, RAW
};

struct DemodConfig {
    DemodMode mode = DemodMode::WFM;
    uint32_t input_rate = 2400000;
    uint32_t output_rate = 48000;
    float bandwidth = 12500.0f;
    bool squelch = false;
    float squelch_db = -70.0f;
    int deemphasis_us = 75; // 0/50/75
    bool noise_reduction = false;
};

class Demodulator {
public:
    Demodulator(const DemodConfig& config = {});
    ~Demodulator() = default;

    void configure(const DemodConfig& config);
    void reset();

    // Process IQ samples (uint8_t interleaved I/Q), output float audio
    // Returns number of output samples written to output buffer
    size_t process(const uint8_t* iq_data, size_t byte_count, float* output);

    void set_mode(DemodMode mode);
    void set_bandwidth(float bw);
    void set_squelch(bool enable, float db);
    void set_deemphasis(int us);
    void set_noise_reduction(bool enable);

    uint32_t get_output_rate() const { return output_rate_; }
    DemodMode get_mode() const { return config_.mode; }

private:
    struct State {
        double prev_phase = 0.0;
        double mix_phase = 0.0;
        double hp_prev_x = 0.0, hp_prev_y = 0.0;
        double dc_i = 0.0, dc_q = 0.0;
        double deemph_state = 0.0;
        double nr_state = 0.0;
        double env = 0.0;
        double acc = 0.0;
        int acc_count = 0;
    } state_;

    DemodConfig config_;
    uint32_t input_rate_ = 2400000;
    uint32_t output_rate_ = 48000;
    int decim_ = 50;

    // FM
    double fm_dev_ = 75000.0;
    double fm_gain_ = 1.0;

    // Shift for SSB/CW
    double shift_hz_ = 0.0;

    // Filters
    double fc_ = 15000.0;
    double lp_b0_ = 0, lp_b1_ = 0, lp_b2_ = 0, lp_a1_ = 0, lp_a2_ = 0;
    double x1_ = 0, x2_ = 0, y1_ = 0, y2_ = 0;
    double hp_alpha_ = 0.0;
    double deemph_alpha_ = 0.0;
    double nr_alpha_ = 0.0;
    double squelch_lin_ = 0.0;

    void rebuild_filters();
    void update_params();
    inline double biquad_lp(double input);
    inline double dc_block(double input);
    inline void apply_squelch(double& sample);
};