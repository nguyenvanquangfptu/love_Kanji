package com.kanjimastery.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void clean_shouldKeepAnOrdinaryUsername() {
        assertThat(LogSanitizer.clean("taro_01")).isEqualTo("taro_01");
    }

    @Test
    void clean_shouldNotLetAUsernameStartAFakeLogLine() {
        assertThat(LogSanitizer.clean("taro\r\nINFO Đăng nhập thành công: user=admin"))
                .doesNotContain("\n", "\r")
                .isEqualTo("taro__INFO Đăng nhập thành công: user=admin");
    }

    @Test
    void clean_shouldCutAVeryLongValue() {
        assertThat(LogSanitizer.clean("a".repeat(500))).hasSize(LogSanitizer.MAX_LENGTH + 3).endsWith("...");
    }

    @Test
    void clean_shouldWriteNullForAMissingValue() {
        assertThat(LogSanitizer.clean(null)).isEqualTo("null");
    }
}
