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
}
