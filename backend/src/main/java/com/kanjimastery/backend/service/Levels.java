package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.JlptLevel;
import org.springframework.util.StringUtils;

/** Đọc cấp độ JLPT người dùng gửi lên ({@code " n4 "} là N4); cấp độ lạ là lỗi của yêu cầu. */
final class Levels {

    private Levels() {
    }

    static JlptLevel require(String value) {
        return JlptLevel.parse(value)
                .orElseThrow(() -> new BadRequestException("Cấp độ JLPT phải là N5, N4, N3, N2 hoặc N1: " + value));
    }

    /** Null nếu không gửi cấp độ (không lọc theo cấp độ). */
    static JlptLevel optional(String value) {
        return StringUtils.hasText(value) ? require(value) : null;
    }
}
