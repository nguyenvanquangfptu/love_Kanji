package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private RateLimiterService rateLimiter;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        // Cấu hình mặc định: sai 10 lần trong 15 phút thì khoá 15 phút.
        loginAttemptService = new LoginAttemptService(rateLimiter, new RateLimitProperties());
    }

    @Test
    void ensureNotLocked_shouldRejectWithRemainingMinutes_whenAccountIsLocked() {
        when(rateLimiter.remaining("login:lock:taro")).thenReturn(Duration.ofSeconds(14 * 60 + 5));

        assertThatThrownBy(() -> loginAttemptService.ensureNotLocked("taro"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15 phút");
    }

    @Test
    void ensureNotLocked_shouldPass_whenAccountIsNotLocked() {
        when(rateLimiter.remaining("login:lock:taro")).thenReturn(null);

        assertThatCode(() -> loginAttemptService.ensureNotLocked("taro")).doesNotThrowAnyException();
    }

    @Test
    void recordFailure_shouldOnlyCount_whenBelowThreshold() {
        when(rateLimiter.increment("login:failures:taro", Duration.ofMinutes(15))).thenReturn(9L);

        assertThatCode(() -> loginAttemptService.recordFailure("taro")).doesNotThrowAnyException();
        verify(rateLimiter, never()).lock(anyString(), any());
    }

    @Test
    void recordFailure_shouldLockAccountAndClearCounter_whenThresholdIsReached() {
        when(rateLimiter.increment("login:failures:taro", Duration.ofMinutes(15))).thenReturn(10L);

        assertThatThrownBy(() -> loginAttemptService.recordFailure("taro"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15 phút");
        verify(rateLimiter).lock("login:lock:taro", Duration.ofMinutes(15));
        verify(rateLimiter).reset("login:failures:taro");
    }

    @Test
    void recordSuccess_shouldClearFailureCounter() {
        loginAttemptService.recordSuccess("taro");

        verify(rateLimiter).reset("login:failures:taro");
    }
}
