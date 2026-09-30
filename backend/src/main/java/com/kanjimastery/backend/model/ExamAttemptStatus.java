package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code user_exam_attempts.status}. */
public final class ExamAttemptStatus {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String TIMEOUT = "TIMEOUT";

    private ExamAttemptStatus() {
    }
}
