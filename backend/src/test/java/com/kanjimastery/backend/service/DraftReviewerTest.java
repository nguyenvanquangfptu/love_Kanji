package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Thứ tự nặng nhẹ của cờ cảnh báo: một câu chỉ giữ cờ nặng nhất. */
class DraftReviewerTest {

    @Test
    void severity_shouldRankWrongAnswerThenAmbiguousThenAboveLevel() {
        assertThat(DraftReviewer.severity(ExamQuestionFlag.WRONG_ANSWER)).isZero();
        assertThat(DraftReviewer.severity(ExamQuestionFlag.AMBIGUOUS)).isEqualTo(1);
        assertThat(DraftReviewer.severity(ExamQuestionFlag.ABOVE_LEVEL)).isEqualTo(2);
    }

    @Test
    void severity_shouldPutLearnerReportsAndStatisticsAboveEveryCheck() {
        assertThat(DraftReviewer.severity(ExamQuestionFlag.REPORTED)).isEqualTo(-1);
        assertThat(DraftReviewer.severity(ExamQuestionFlag.STATS)).isEqualTo(-1);
    }

    @Test
    void flag_shouldKeepOnlyTheMostSevereFlag_andCollectEveryNote() {
        ExamQuestion question = new ExamQuestion();

        DraftReviewer.flag(question, ExamQuestionFlag.ABOVE_LEVEL, "Từ vượt cấp.");
        DraftReviewer.flag(question, ExamQuestionFlag.WRONG_ANSWER, "Máy chọn B.");
        DraftReviewer.flag(question, ExamQuestionFlag.AMBIGUOUS, "C cũng đúng.");

        assertThat(question.getFlag()).isEqualTo(ExamQuestionFlag.WRONG_ANSWER);
        assertThat(question.getReviewNote()).isEqualTo("Từ vượt cấp. Máy chọn B. C cũng đúng.");
    }

    @Test
    void flag_shouldNotReplaceAReportedQuestionsFlag() {
        ExamQuestion question = new ExamQuestion();
        question.setFlag(ExamQuestionFlag.REPORTED);

        DraftReviewer.flag(question, ExamQuestionFlag.WRONG_ANSWER, "Máy chọn B.");

        assertThat(question.getFlag()).isEqualTo(ExamQuestionFlag.REPORTED);
    }
}
