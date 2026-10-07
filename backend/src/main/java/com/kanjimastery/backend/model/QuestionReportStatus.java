package com.kanjimastery.backend.model;

/** Trạng thái một báo lỗi (cột {@code exam_question_reports.status}). */
public enum QuestionReportStatus {
    /** Chờ người duyệt xem. */
    OPEN,
    /** Người duyệt đã xử lý câu (duyệt lại, loại, rút khỏi đề). */
    RESOLVED,
    /** Người duyệt xem và thấy câu không sai. */
    DISMISSED
}
