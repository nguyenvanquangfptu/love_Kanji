package com.kanjimastery.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Danh sách đen cho Access Token (theo jti) khi user chủ động logout,
 * để token còn hạn không thể dùng lại được nữa dù chưa hết TTL.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
