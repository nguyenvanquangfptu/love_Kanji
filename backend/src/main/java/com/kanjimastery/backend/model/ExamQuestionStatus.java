package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_questions.status}: chỉ câu {@link #APPROVED} mới được lấy vào đề. */
public final class ExamQuestionStatus {
    /** Nháp, chờ duyệt. */
    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** Đã dùng rồi rút khỏi đề (vd. bị báo sai). */
    public static final String RETIRED = "RETIRED";

    private ExamQuestionStatus() {
    }
}
