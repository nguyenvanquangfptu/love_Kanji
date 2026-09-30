package com.kanjimastery.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bộ đếm cửa sổ cố định và khoá có hạn trên Redis, dùng chung cho mọi giới hạn tần suất:
 * đăng nhập/đăng ký theo IP, khoá tài khoản sai mật khẩu, tạo trắc nghiệm theo tài khoản,
 * số lần sinh câu thất bại của từng từ và trần request Gemini mỗi ngày.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    /** INCR và đặt hạn trong cùng một lệnh nguyên tử - không bao giờ để lại bộ đếm không có hạn (chặn vĩnh viễn). */
    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** Tăng bộ đếm, cửa sổ tính từ lần đếm đầu tiên. Trả về giá trị sau khi tăng. */
    public long increment(String key, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }

    /** Đếm thêm một lần và cho biết còn trong giới hạn không: lần thứ {@code limit + 1} trở đi bị từ chối. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        return increment(key, window) <= limit;
    }

    /** Đọc nhiều bộ đếm trong một lần gọi Redis, đúng thứ tự {@code keys}; khoá không tồn tại tính là 0. */
    public List<Long> counts(List<String> keys) {
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return keys.stream().map(key -> 0L).toList();
        }
        return values.stream().map(value -> value == null ? 0L : Long.parseLong(value)).toList();
    }

    public void lock(String key, Duration duration) {
        redisTemplate.opsForValue().set(key, "1", duration);
    }

    /** Thời gian còn lại của khoá, hoặc {@code null} nếu khoá không tồn tại. */
    public Duration remaining(String key) {
        Long millis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return millis == null || millis < 0 ? null : Duration.ofMillis(millis);
    }

    public void reset(String key) {
        redisTemplate.delete(key);
    }
}
