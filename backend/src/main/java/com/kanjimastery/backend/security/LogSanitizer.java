package com.kanjimastery.backend.security;

/**
 * Làm sạch chuỗi do người dùng gõ (tên đăng nhập, đường dẫn...) trước khi đưa vào log: thay ký tự điều khiển
 * (xuống dòng, tab...) bằng {@code _} để không ai giả được một dòng log khác (log injection), và cắt bớt chuỗi quá dài.
 */
public final class LogSanitizer {

    static final int MAX_LENGTH = 100;

    private LogSanitizer() {
    }

    public static String clean(String value) {
        if (value == null) {
            return "null";
        }
        String oneLine = value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "_");
        return oneLine.length() > MAX_LENGTH ? oneLine.substring(0, MAX_LENGTH) + "..." : oneLine;
    }
}
