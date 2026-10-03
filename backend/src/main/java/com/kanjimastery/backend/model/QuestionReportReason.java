package com.kanjimastery.backend.model;

import java.util.Set;

/** Lý do người học báo lỗi một câu hỏi (cột {@code exam_question_reports.reason}). */
public final class QuestionReportReason {
    /** Đáp án được chấm đúng là sai. */
    public static final String WRONG_ANSWER = "WRONG_ANSWER";
    /** Có hơn một đáp án đúng. */
    public static final String AMBIGUOUS = "AMBIGUOUS";
    /** Câu khó hiểu, viết sai, lỗi chính tả. */
    public static final String UNCLEAR = "UNCLEAR";
    public static final String OTHER = "OTHER";

    public static final Set<String> ALL = Set.of(WRONG_ANSWER, AMBIGUOUS, UNCLEAR, OTHER);

    private QuestionReportReason() {
    }
}
