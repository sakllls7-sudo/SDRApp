#pragma once

#include <cstdint>
#include <cstddef>
#include <atomic>
#include <vector>
#include <algorithm>

template <typename T>
class RingBuffer {
public:
    explicit RingBuffer(size_t capacity)
        : capacity_(capacity), data_(capacity), head_(0), tail_(0), size_(0) {}

    // Non-copyable, movable
    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;
    RingBuffer(RingBuffer&&) = default;
    RingBuffer& operator=(RingBuffer&&) = default;

    // Write data, returns number of elements written
    size_t write(const T* src, size_t count) {
        size_t available = capacity_ - size_.load(std::memory_order_acquire);
        size_t to_write = std::min(count, available);
        if (to_write == 0) return 0;

        size_t head = head_.load(std::memory_order_relaxed);
        size_t first_chunk = std::min(to_write, capacity_ - head);
        std::copy(src, src + first_chunk, data_.data() + head);
        if (to_write > first_chunk) {
            std::copy(src + first_chunk, src + to_write, data_.data());
        }
        head_.store((head + to_write) % capacity_, std::memory_order_release);
        size_.fetch_add(to_write, std::memory_order_release);
        return to_write;
    }

    // Read data, returns number of elements read
    size_t read(T* dst, size_t count) {
        size_t available = size_.load(std::memory_order_acquire);
        size_t to_read = std::min(count, available);
        if (to_read == 0) return 0;

        size_t tail = tail_.load(std::memory_order_relaxed);
        size_t first_chunk = std::min(to_read, capacity_ - tail);
        std::copy(data_.data() + tail, data_.data() + tail + first_chunk, dst);
        if (to_read > first_chunk) {
            std::copy(data_.data(), data_.data() + (to_read - first_chunk), dst + first_chunk);
        }
        tail_.store((tail + to_read) % capacity_, std::memory_order_release);
        size_.fetch_sub(to_read, std::memory_order_release);
        return to_read;
    }

    // Peek without consuming
    size_t peek(T* dst, size_t count) const {
        size_t available = size_.load(std::memory_order_acquire);
        size_t to_read = std::min(count, available);
        if (to_read == 0) return 0;

        size_t tail = tail_.load(std::memory_order_relaxed);
        size_t first_chunk = std::min(to_read, capacity_ - tail);
        std::copy(data_.data() + tail, data_.data() + tail + first_chunk, dst);
        if (to_read > first_chunk) {
            std::copy(data_.data(), data_.data() + (to_read - first_chunk), dst + first_chunk);
        }
        return to_read;
    }

    // Advance tail without copying (consume)
    void consume(size_t count) {
        size_t available = size_.load(std::memory_order_acquire);
        size_t to_consume = std::min(count, available);
        if (to_consume == 0) return;
        size_t tail = tail_.load(std::memory_order_relaxed);
        tail_.store((tail + to_consume) % capacity_, std::memory_order_release);
        size_.fetch_sub(to_consume, std::memory_order_release);
    }

    size_t size() const { return size_.load(std::memory_order_acquire); }
    size_t capacity() const { return capacity_; }
    size_t available() const { return capacity_ - size_.load(std::memory_order_acquire); }
    bool empty() const { return size_.load(std::memory_order_acquire) == 0; }
    bool full() const { return size_.load(std::memory_order_acquire) == capacity_; }

    void clear() {
        head_.store(0, std::memory_order_release);
        tail_.store(0, std::memory_order_release);
        size_.store(0, std::memory_order_release);
    }

private:
    const size_t capacity_;
    std::vector<T> data_;
    std::atomic<size_t> head_;
    std::atomic<size_t> tail_;
    std::atomic<size_t> size_;
};

// Packer: собирает сэмплы в блоки фиксированного размера (как в SDR++)
template <typename T>
class Packer {
public:
    Packer(RingBuffer<T>& output_buffer, size_t block_size)
        : output_(output_buffer), block_size_(block_size), partial_count_(0) {
        partial_.resize(block_size);
    }

    // Добавить сэмплы, упаковать в блоки
    void push(const T* data, size_t count) {
        size_t i = 0;
        while (i < count) {
            size_t needed = block_size_ - partial_count_;
            size_t take = std::min(needed, count - i);
            std::copy(data + i, data + i + take, partial_.data() + partial_count_);
            partial_count_ += take;
            i += take;

            if (partial_count_ == block_size_) {
                flush_block();
            }
        }
    }

    // Сбросить частичный блок (дополнить нулями)
    void flush_partial() {
        if (partial_count_ > 0) {
            std::fill(partial_.data() + partial_count_, partial_.data() + block_size_, T{0});
            flush_block();
        }
    }

    void stop() { output_.clear(); }

private:
    void flush_block() {
        while (output_.available() < block_size_) {
            // Блокируемся если буфер полон (busy wait с паузой)
            std::this_thread::sleep_for(std::chrono::microseconds(100));
        }
        output_.write(partial_.data(), block_size_);
        partial_count_ = 0;
    }

    RingBuffer<T>& output_;
    const size_t block_size_;
    std::vector<T> partial_;
    size_t partial_count_ = 0;
};