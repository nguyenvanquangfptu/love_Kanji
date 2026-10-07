package com.kanjimastery.backend.model;

import java.util.Locale;
import java.util.Optional;

/** Cấp độ JLPT, theo thứ tự từ dễ (N5) tới khó (N1) - {@link #ordinal()} càng lớn càng khó. */
public enum JlptLevel {
    N5,
    N4,
    N3,
    N2,
    N1;

    /** Cấp độ người học gõ vào: bỏ khoảng trắng, không phân biệt hoa thường ({@code " n4 "} là N4); rỗng nếu không phải N1-N5. */
    public static Optional<JlptLevel> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        for (JlptLevel level : values()) {
            if (level.name().equals(normalized)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    /** Khó hơn hoặc bằng {@code other}. */
    public boolean isAtLeast(JlptLevel other) {
        return ordinal() >= other.ordinal();
    }
}
