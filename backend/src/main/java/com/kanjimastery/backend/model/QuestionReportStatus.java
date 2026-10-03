package com.kanjimastery.backend.model;

/** Trạng thái một báo lỗi (cột {@code exam_question_reports.status}). */
public final class QuestionReportStatus {
    /** Chờ người duyệt xem. */
    public static final String OPEN = "OPEN";
    /** Người duyệt đã xử lý câu (duyệt lại, loại, rút khỏi đề). */
    public static final String RESOLVED = "RESOLVED";
    /** Người duyệt xem và thấy câu không sai. */
    public static final String DISMISSED = "DISMISSED";

    private QuestionReportStatus() {
    }
}
