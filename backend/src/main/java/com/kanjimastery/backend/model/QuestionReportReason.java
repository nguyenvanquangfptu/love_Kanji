package com.kanjimastery.backend.model;

/** Lý do người học báo lỗi một câu hỏi (cột {@code exam_question_reports.reason}). */
public enum QuestionReportReason {
    /** Đáp án được chấm đúng là sai. */
    WRONG_ANSWER,
    /** Có hơn một đáp án đúng. */
    AMBIGUOUS,
    /** Câu khó hiểu, viết sai, lỗi chính tả. */
    UNCLEAR,
    OTHER
}
