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
    /** Buổi làm đề JLPT và phần đang làm ({@link com.kanjimastery.backend.model.ExamSection}); null với thi nhanh. */
    private Long sittingId;
    private String section;
    /** Các 問題 của phần đề JLPT - câu hỏi xếp liền nhau theo thứ tự này; null với thi nhanh. */
    private List<ExamMondaiResponse> mondai;
}
