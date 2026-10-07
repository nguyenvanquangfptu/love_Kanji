package com.kanjimastery.backend.model;

/** Các giá trị của cột {@code exam_questions.flag}: cảnh báo cho người duyệt (kiểm tra tự động, người học báo lỗi). */
public enum ExamQuestionFlag {
    /** Máy giải lại chọn một đáp án khác với đáp án đã cho. */
    WRONG_ANSWER(0),
    /** Máy giải lại thấy có hơn một đáp án hợp. */
    AMBIGUOUS(1),
    /** Câu dùng nhiều từ vượt cấp độ. */
    ABOVE_LEVEL(2),
    /** Nhiều người học báo lỗi - câu đã tự rút khỏi đề. */
    REPORTED(-1),
    /** Phân tích kết quả thi thấy câu đáng ngờ (người làm tốt lại hay sai) - câu vẫn trong đề. */
    STATS(-1);

    private final int severity;

    ExamQuestionFlag(int severity) {
        this.severity = severity;
    }

    /**
     * Thứ tự nặng nhẹ khi một câu chỉ giữ một cờ: số nhỏ là nặng hơn. Báo lỗi của người học và kết quả phân tích (-1)
     * nặng hơn mọi bước kiểm tra tự động, để bước kiểm tra không đè mất chúng.
     */
    public int severity() {
        return severity;
    }
}
