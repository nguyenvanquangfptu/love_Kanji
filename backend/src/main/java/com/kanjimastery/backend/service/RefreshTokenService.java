package com.kanjimastery.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import com.kanjimastery.backend.config.JwtProperties;

/**
 * Quản lý Refresh Token còn hiệu lực trong Redis để hỗ trợ Rotation:
 * mỗi token chỉ dùng được đúng 1 lần refresh, dùng lại token đã rotate
 * bị coi là dấu hiệu bị đánh cắp và toàn bộ session của user sẽ bị thu hồi.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void store(Long userId, String jti) {
        Duration ttl = Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs());
        redisTemplate.opsForValue().set(detailKey(userId, jti), "1", ttl);
        redisTemplate.opsForSet().add(activeSetKey(userId), jti);
    }

    public boolean isValid(Long userId, String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(detailKey(userId, jti)));
    }

    public void revoke(Long userId, String jti) {
        redisTemplate.delete(detailKey(userId, jti));
        redisTemplate.opsForSet().remove(activeSetKey(userId), jti);
    }

    public void revokeAll(Long userId) {
        Set<String> activeJtis = redisTemplate.opsForSet().members(activeSetKey(userId));
        if (activeJtis != null) {
            for (String jti : activeJtis) {
                redisTemplate.delete(detailKey(userId, jti));
            }
        }
        redisTemplate.delete(activeSetKey(userId));
    }

    private String detailKey(Long userId, String jti) {
        return "refresh:%d:%s".formatted(userId, jti);
    }

    private String activeSetKey(Long userId) {
        return "refresh:active:" + userId;
    }
}
