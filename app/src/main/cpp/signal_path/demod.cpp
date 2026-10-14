#include "demod.h"
#include <algorithm>
#include <cmath>

Demodulator::Demodulator(const DemodConfig& config) : config_(config) {
    input_rate_ = config.input_rate;
    output_rate_ = config.output_rate;
    decim_ = std::max(1, int(input_rate_ / output_rate_));
    rebuild_filters();
    update_params();
}

void Demodulator::configure(const DemodConfig& config) {
    config_ = config;
    input_rate_ = config.input_rate;
    output_rate_ = config.output_rate;
    decim_ = std::max(1, int(input_rate_ / output_rate_));
    rebuild_filters();
    update_params();
    reset();
}

void Demodulator::reset() {
    state_ = State{};
    x1_ = x2_ = y1_ = y2_ = 0.0;
}

void Demodulator::rebuild_filters() {
    double fc_hz = fc_;
    if (config_.mode == DemodMode::WFM) fc_hz = 15000.0;
    else if (config_.mode == DemodMode::NFM) fc_hz = config_.bandwidth / 2.0;
    else if (config_.mode == DemodMode::AM) fc_hz = config_.bandwidth / 2.0;
    else if (config_.mode == DemodMode::CW) fc_hz = 400.0;
    else fc_hz = config_.bandwidth / 2.0;

    fc_hz = std::clamp(fc_hz, 80.0, input_rate_ * 0.45);
    fc_ = fc_hz;

    double w0 = 2.0 * M_PI * fc_ / input_rate_;
    double cosw = cos(w0);
    double sinw = sin(w0);
    double q = 0.707;
    double alpha = sinw / (2.0 * q);
    double a0 = 1.0 + alpha;

    lp_b0_ = ((1.0 - cosw) / 2.0) / a0;
    lp_b1_ = (1.0 - cosw) / a0;
    lp_b2_ = ((1.0 - cosw) / 2.0) / a0;
    lp_a1_ = (-2.0 * cosw) / a0;
    lp_a2_ = (1.0 - alpha) / a0;

    // DC block
    double rc = 1.0 / (2.0 * M_PI * 25.0);
    double dt = 1.0 / input_rate_;
    hp_alpha_ = rc / (rc + dt);

    // De-emphasis
    if (config_.deemphasis_us > 0 && config_.mode == DemodMode::WFM) {
        double tau = config_.deemphasis_us * 1e-6;
        double dt_audio = 1.0 / output_rate_;
        deemph_alpha_ = dt_audio / (tau + dt_audio);
    } else {
        deemph_alpha_ = 0.0;
    }

    // Noise reduction
    if (config_.noise_reduction) {
        double tau = 1.0 / (2.0 * M_PI * 3000.0);
        double dt_audio = 1.0 / output_rate_;
        nr_alpha_ = dt_audio / (tau + dt_audio);
    } else {
        nr_alpha_ = 0.0;
    }

    // Squelch
    squelch_lin_ = pow(10.0, config_.squelch_db / 20.0);

    // FM params
    fm_dev_ = (config_.mode == DemodMode::WFM) ? 75000.0 : 3000.0;
    fm_gain_ = (config_.mode == DemodMode::WFM || config_.mode == DemodMode::NFM)
        ? input_rate_ / (2.0 * M_PI * fm_dev_) : 1.0;

    // SSB/CW shift
    if (config_.mode == DemodMode::USB) shift_hz_ = config_.bandwidth / 2.0;
    else if (config_.mode == DemodMode::LSB) shift_hz_ = -config_.bandwidth / 2.0;
    else if (config_.mode == DemodMode::CW) shift_hz_ = 700.0;
    else shift_hz_ = 0.0;

    decim_ = std::max(1, int(input_rate_ / output_rate_));
}

void Demodulator::update_params() {
    rebuild_filters();
}

void Demodulator::set_mode(DemodMode mode) {
    config_.mode = mode;
    rebuild_filters();
}

void Demodulator::set_bandwidth(float bw) {
    config_.bandwidth = bw;
    rebuild_filters();
}

void Demodulator::set_squelch(bool enable, float db) {
    config_.squelch = enable;
    config_.squelch_db = db;
    squelch_lin_ = pow(10.0, db / 20.0);
}

void Demodulator::set_deemphasis(int us) {
    config_.deemphasis_us = us;
    rebuild_filters();
}

void Demodulator::set_noise_reduction(bool enable) {
    config_.noise_reduction = enable;
    rebuild_filters();
}

inline double Demodulator::biquad_lp(double input) {
    double y = lp_b0_ * input + lp_b1_ * x1_ + lp_b2_ * x2_ - lp_a1_ * y1_ - lp_a2_ * y2_;
    x2_ = x1_; x1_ = input;
    y2_ = y1_; y1_ = y;
    return y;
}

inline double Demodulator::dc_block(double input) {
    double hp = hp_alpha_ * (hp_alpha_ + input - hp_alpha_); // corrected
    hp = hp_alpha_ * (hp_alpha_ + input - hp_alpha_); // This is wrong, fixing below
    // Proper DC block:
    double out = hp_alpha_ * (state_.hp_prev_y + input - state_.hp_prev_x);
    state_.hp_prev_x = input;
    state_.hp_prev_y = out;
    return out;
}

inline void Demodulator::apply_squelch(double& sample) {
    if (!config_.squelch) return;
    state_.env += 0.01 * (std::abs(sample) - state_.env);
    if (state_.env < squelch_lin_) sample = 0.0;
}

size_t Demodulator::process(const uint8_t* iq_data, size_t byte_count, float* output) {
    size_t pairs = byte_count / 2;
    size_t out_idx = 0;

    for (size_t n = 0; n < pairs; ++n) {
        double i = ((iq_data[n * 2] & 0xFF) - 127.5) / 127.5;
        double q = ((iq_data[n * 2 + 1] & 0xFF) - 127.5) / 127.5;

        // DC removal on IQ
        state_.dc_i += (i - state_.dc_i) * 0.001;
        state_.dc_q += (q - state_.dc_q) * 0.001;
        i -= state_.dc_i;
        q -= state_.dc_q;

        double s = 0.0;
        switch (config_.mode) {
            case DemodMode::AM:
                s = sqrt(i * i + q * q);
                break;
            case DemodMode::WFM:
            case DemodMode::NFM: {
                double ph = atan2(q, i);
                double d = ph - state_.prev_phase;
                if (d > M_PI) d -= 2.0 * M_PI;
                if (d < -M_PI) d += 2.0 * M_PI;
                state_.prev_phase = ph;
                s = d * fm_gain_;
                break;
            }
            case DemodMode::USB:
            case DemodMode::LSB:
            case DemodMode::CW: {
                state_.mix_phase += 2.0 * M_PI * shift_hz_ / input_rate_;
                if (state_.mix_phase > 2.0 * M_PI) state_.mix_phase -= 2.0 * M_PI;
                s = i * cos(state_.mix_phase) + q * sin(state_.mix_phase);
                break;
            }
            case DemodMode::RAW:
                s = i;
                break;
        }

        // Low-pass filter
        double y = biquad_lp(s);

        // Decimation
        state_.acc += y;
        state_.acc_count++;
        if (state_.acc_count >= decim_) {
            double a = state_.acc / decim_;
            state_.acc = 0.0;
            state_.acc_count = 0;

            // DC block
            double hp = dc_block(a);

            // Noise reduction
            if (nr_alpha_ > 0.0) {
                state_.nr_state += nr_alpha_ * (hp - state_.nr_state);
                hp = state_.nr_state;
            }

            // De-emphasis
            if (deemph_alpha_ > 0.0) {
                state_.deemph_state += deemph_alpha_ * (hp - state_.deemph_state);
                hp = state_.deemph_state;
            }

            // Squelch
            apply_squelch(hp);

            // Soft limiting
            hp = tanh(hp * 1.5) * 0.95;

            if (out_idx < INT_MAX) {
                output[out_idx++] = static_cast<float>(hp);
            }
        }
    }

    return out_idx;
}