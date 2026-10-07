package com.kanjimastery.backend.model;

/** Hướng hỏi của một câu trắc nghiệm. */
public enum QuizDirection {
    /** Cho chữ Hán, chọn cách đọc. */
    KANJI_TO_READING,
    /** Cho cách đọc, chọn cách viết chữ Hán. */
    READING_TO_KANJI,
    /** Cho từ, chọn nghĩa (từ không có chữ Hán thì chỉ hỏi được kiểu này). */
    MEANING
}
