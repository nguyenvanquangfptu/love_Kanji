package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class StartExamResponse {
    private Long attemptId;
    private String jlptLevel;
    private List<ExamQuestionPublicResponse> questions;
    /** Frontend dùng số giây này để đếm ngược cục bộ, tránh lệch giờ do đồng hồ client sai (clock drift). */
    private long remainingSeconds;
    private LocalDateTime startedAt;
}
