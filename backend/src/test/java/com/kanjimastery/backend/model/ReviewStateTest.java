package com.kanjimastery.backend.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Quy tắc đổi trạng thái duyệt dùng chung cho câu thi và đoạn văn. */
class ReviewStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);

    @Test
    void decide_shouldStampTheReview_andClearTheFlagOnlyWhenApproving() {
        ReviewState rejected = new ReviewState(ExamQuestionStatus.DRAFT, ExamQuestionFlag.AMBIGUOUS, "A cũng đúng", null);
        rejected.decide(ExamQuestionStatus.REJECTED, NOW);
        assertThat(rejected.getFlag()).isEqualTo(ExamQuestionFlag.AMBIGUOUS);
        assertThat(rejected.getReviewedAt()).isEqualTo(NOW);

        ReviewState approved = new ReviewState(ExamQuestionStatus.DRAFT, ExamQuestionFlag.AMBIGUOUS, "A cũng đúng", null);
        approved.decide(ExamQuestionStatus.APPROVED, NOW);
        assertThat(approved.getStatus()).isEqualTo(ExamQuestionStatus.APPROVED);
        assertThat(approved.getFlag()).isNull();
        assertThat(approved.getReviewNote()).isEqualTo("A cũng đúng");
    }

    @Test
    void moveTo_shouldNotPretendSomeoneReviewedTheQuestion() {
        ReviewState state = new ReviewState(ExamQuestionStatus.APPROVED);

        state.moveTo(ExamQuestionStatus.DRAFT);

        assertThat(state.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(state.getReviewedAt()).isNull();
    }

    @Test
    void raise_shouldKeepTheMostSevereFlag_whileReplaceFlagAlwaysWins() {
        ReviewState state = new ReviewState(ExamQuestionStatus.DRAFT);

        state.raise(ExamQuestionFlag.ABOVE_LEVEL, "Từ vượt cấp.");
        state.raise(ExamQuestionFlag.WRONG_ANSWER, null);
        state.raise(ExamQuestionFlag.AMBIGUOUS, "C cũng đúng.");
        assertThat(state.getFlag()).isEqualTo(ExamQuestionFlag.WRONG_ANSWER);
        assertThat(state.getReviewNote()).isEqualTo("Từ vượt cấp. C cũng đúng.");

        state.raise(ExamQuestionFlag.STATS, null);
        state.replaceFlag(ExamQuestionFlag.REPORTED, "3 người báo lỗi.");
        assertThat(state.getFlag()).isEqualTo(ExamQuestionFlag.REPORTED);
        assertThat(state.getReviewNote()).endsWith("3 người báo lỗi.");
    }
}
