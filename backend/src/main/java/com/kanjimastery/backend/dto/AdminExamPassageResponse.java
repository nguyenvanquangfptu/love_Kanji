package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Một đoạn văn 文章の文法 trên trang duyệt, kèm các câu hỏi theo thứ tự chỗ trống. */
@Getter
@Builder
@AllArgsConstructor
public class AdminExamPassageResponse {
    private Long id;
    private String jlptLevel;
    private String title;
    /** Chỗ trống đánh dấu 【1】【2】... */
    private String content;
    /** {@link com.kanjimastery.backend.model.ExamQuestionStatus} */
    private String status;
    /** {@link com.kanjimastery.backend.model.ExamQuestionSource} */
    private String source;
    /** {@link com.kanjimastery.backend.model.ExamQuestionFlag}; null = không có cảnh báo. */
    private String flag;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private List<AdminExamQuestionResponse> questions;
}
