package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamAttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ExamResultResponse {
    private Long attemptId;
    private ExamAttemptStatus status;
    private Integer totalScore;
    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;
    /** Buổi làm đề JLPT của lượt thi này - nộp xong thì quay về buổi thi; null với thi nhanh. */
    private Long sittingId;
}
