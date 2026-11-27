package com.kanjimastery.backend.model;

import java.util.Arrays;
import java.util.List;

/** Hướng hỏi của một câu trắc nghiệm (cột {@code review_logs.direction}, và {@code exam_questions.skill} với câu thi). */
public enum QuizDirection {
    /** Cho chữ Hán, chọn cách đọc. */
    KANJI_TO_READING(true),
    /** Cho cách đọc, chọn cách viết chữ Hán. */
    READING_TO_KANJI(true),
    /** Cho từ, chọn nghĩa (từ không có chữ Hán thì chỉ hỏi được kiểu này). */
    MEANING(true),
    /** Cho chữ Hán, tự gõ cách đọc (romaji hoặc kana) - chỉ có ở phần học, không có câu thi. */
    TYPE_READING(false),
    /** Thẻ ôn hỏi ngược: cho nghĩa, người học tự nhớ ra từ rồi tự chấm (nguồn FLASHCARD, không có đáp án để chọn). */
    RECALL_WORD(false),
    /** Câu ví dụ che mất từ, chọn từ điền vào chỗ trống. */
    CLOZE(false),
    /** Nghe cách đọc (giọng đọc của trình duyệt), chọn nghĩa - không thấy chữ. */
    LISTENING(false);

    private final boolean examSkill;

    QuizDirection(boolean examSkill) {
        this.examSkill = examSkill;
    }

    /** Kỹ năng câu thi trong ngân hàng đề kiểm tra, theo thứ tự chia câu và báo điểm: đọc, viết, nghĩa. */
    public static List<QuizDirection> examSkills() {
        return Arrays.stream(values()).filter(direction -> direction.examSkill).toList();
    }
}
