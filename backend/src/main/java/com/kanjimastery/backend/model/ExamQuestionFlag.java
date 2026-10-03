package com.kanjimastery.backend.model;

/** Các giá trị của cột {@code exam_questions.flag}: cảnh báo cho người duyệt (kiểm tra tự động, người học báo lỗi). */
public final class ExamQuestionFlag {
    /** Máy giải lại thấy có hơn một đáp án hợp. */
    public static final String AMBIGUOUS = "AMBIGUOUS";
    /** Máy giải lại chọn một đáp án khác với đáp án đã cho. */
    public static final String WRONG_ANSWER = "WRONG_ANSWER";
    /** Câu dùng nhiều từ vượt cấp độ. */
    public static final String ABOVE_LEVEL = "ABOVE_LEVEL";
    /** Nhiều người học báo lỗi - câu đã tự rút khỏi đề. */
    public static final String REPORTED = "REPORTED";

    private ExamQuestionFlag() {
    }
}
