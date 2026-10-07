package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_questions.status}: chỉ câu {@link #APPROVED} mới được lấy vào đề. */
public enum ExamQuestionStatus {
    /** Nháp, chờ duyệt. */
    DRAFT,
    APPROVED,
    REJECTED,
    /** Đã dùng rồi rút khỏi đề (vd. bị báo sai). */
    RETIRED
}
