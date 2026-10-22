package com.kanjimastery.backend.dto;

/**
 * Một đoạn của câu tiếng Nhật đã chia để hiện furigana. Ghép {@code text} của các đoạn theo thứ tự ra lại đúng câu gốc.
 *
 * @param text   chữ của đoạn
 * @param ruby   furigana (hiragana) đặt trên {@code text}; null nếu đoạn không có chữ Hán hoặc không được hiện cách đọc
 * @param target đoạn thuộc lần xuất hiện đầu tiên của từ được hỏi - giao diện gạch chân
 */
public record RubySegment(String text, String ruby, boolean target) {
}
