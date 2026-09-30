package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Khoá đăng nhập theo tài khoản: sai mật khẩu quá nhiều lần trong một khoảng thời gian thì tạm khoá
 * tài khoản đó, dù request đến từ IP nào. Bổ sung cho giới hạn theo IP ở RateLimitFilter - kẻ dò mật khẩu
 * đổi IP liên tục vẫn bị chặn.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAILURES_PREFIX = "login:failures:";
    private static final String LOCK_PREFIX = "login:lock:";

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public void ensureNotLocked(String username) {
        Duration remaining = rateLimiter.remaining(LOCK_PREFIX + username);
        if (remaining != null) {
            throw lockedException(remaining);
        }
    }

    /** Ghi nhận một lần sai mật khẩu; chạm ngưỡng thì khoá tài khoản ngay và báo lỗi khoá thay cho lỗi sai mật khẩu. */
    public void recordFailure(String username) {
        RateLimitProperties.LoginLock config = rateLimitProperties.getLoginLock();
        long failures = rateLimiter.increment(FAILURES_PREFIX + username, Duration.ofSeconds(config.getWindowSeconds()));
        if (failures >= config.getMaxFailures()) {
            Duration lockDuration = Duration.ofSeconds(config.getLockSeconds());
            rateLimiter.lock(LOCK_PREFIX + username, lockDuration);
            rateLimiter.reset(FAILURES_PREFIX + username);
            throw lockedException(lockDuration);
        }
    }

    public void recordSuccess(String username) {
        rateLimiter.reset(FAILURES_PREFIX + username);
    }

    private TooManyRequestsException lockedException(Duration remaining) {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        return new TooManyRequestsException(
                "Tài khoản tạm khoá vì đăng nhập sai quá nhiều lần. Vui lòng thử lại sau %d phút.".formatted(minutes));
    }
}
