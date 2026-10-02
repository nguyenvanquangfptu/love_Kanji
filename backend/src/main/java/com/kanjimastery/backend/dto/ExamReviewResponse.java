package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExamReviewResponse {
    private Long attemptId;
    private String jlptLevel;
    private String status;
    private Integer totalScore;
    private Integer totalQuestions;
    private Integer timeSpentSeconds;
    private List<QuestionReviewItem> questions;
    /** Điểm theo kỹ năng, theo thứ tự đọc - viết - nghĩa; kỹ năng không có câu nào trong bài thì không có. */
    private List<SkillScore> skills;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SkillScore {
        /** Như hướng hỏi trắc nghiệm: KANJI_TO_READING (đọc), READING_TO_KANJI (viết), MEANING (nghĩa). */
        private String skill;
        private int correct;
        private int total;
    }
}
