package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ExamResultResponse {
    private Long attemptId;
    private String status;
    private Integer totalScore;
    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;
}
