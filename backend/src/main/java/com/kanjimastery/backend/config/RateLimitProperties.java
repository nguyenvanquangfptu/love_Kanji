package com.kanjimastery.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private Bucket login = new Bucket();
    private Bucket register = new Bucket();
    /** Tạo trắc nghiệm - đếm theo tài khoản, không theo IP. */
    private Bucket quiz = new Bucket();
    private LoginLock loginLock = new LoginLock();

    @Getter
    @Setter
    public static class Bucket {
        private int limit;
        private int windowSeconds;
    }

    /** Sai mật khẩu {@code maxFailures} lần trong {@code windowSeconds} thì khoá tài khoản {@code lockSeconds}. */
    @Getter
    @Setter
    public static class LoginLock {
        private int maxFailures = 10;
        private int windowSeconds = 900;
        private int lockSeconds = 900;
    }
}
