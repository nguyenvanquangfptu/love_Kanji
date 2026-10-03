package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_questions.source}: câu thi soạn tay, sinh từ kho từ vựng hay do AI viết nháp. */
public final class ExamQuestionSource {
    public static final String MANUAL = "MANUAL";
    public static final String GENERATED = "GENERATED";
    /** AI (Gemini) viết nháp, phải qua người duyệt mới vào đề. */
    public static final String AI = "AI";

    private ExamQuestionSource() {
    }
}
