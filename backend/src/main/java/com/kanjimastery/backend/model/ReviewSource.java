package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code review_logs.source}: người học trả lời từ ở đâu. */
public final class ReviewSource {
    public static final String FLASHCARD = "FLASHCARD";
    public static final String QUIZ = "QUIZ";
    public static final String EXAM = "EXAM";

    private ReviewSource() {
    }
}
