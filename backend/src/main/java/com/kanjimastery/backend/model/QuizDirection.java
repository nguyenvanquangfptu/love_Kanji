package com.kanjimastery.backend.model;

/** Hướng hỏi của một câu trắc nghiệm. */
public final class QuizDirection {
    /** Cho chữ Hán, chọn cách đọc. */
    public static final String KANJI_TO_READING = "KANJI_TO_READING";
    /** Cho cách đọc, chọn cách viết chữ Hán. */
    public static final String READING_TO_KANJI = "READING_TO_KANJI";
    /** Cho từ, chọn nghĩa (từ không có chữ Hán thì chỉ hỏi được kiểu này). */
    public static final String MEANING = "MEANING";

    private QuizDirection() {
    }
}
