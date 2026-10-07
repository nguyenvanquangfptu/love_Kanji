package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
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
    private ExamQuestionStatus status;
    /** {@link com.kanjimastery.backend.model.ExamQuestionSource} */
    private ExamQuestionSource source;
    /** {@link com.kanjimastery.backend.model.ExamQuestionFlag}; null = không có cảnh báo. */
    private ExamQuestionFlag flag;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private List<AdminExamQuestionResponse> questions;
}
