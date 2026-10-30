package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Một đoạn văn (文章の文法 hoặc bài đọc) trên trang duyệt, kèm các câu hỏi theo thứ tự chỗ trống / thứ tự câu. */
@Getter
@Builder
@AllArgsConstructor
public class AdminExamPassageResponse {
    private Long id;
    private JlptLevel jlptLevel;
    /** {@link com.kanjimastery.backend.model.ExamSection}: GRAMMAR (文章の文法) hoặc READING (bài đọc). */
    private ExamSection section;
    private String title;
    /** Ngữ pháp: chỗ trống đánh dấu 【1】【2】...; Đọc hiểu: gạch chân 〔下線〕...〔/下線〕, bảng | ô | ô |. */
    private String content;
    /** {@link com.kanjimastery.backend.model.ExamQuestionStatus} */
    private ExamQuestionStatus status;
    /** {@link com.kanjimastery.backend.model.ExamQuestionSource} */
    private ExamQuestionSource source;
    /** Đoạn văn nào của đề tự soạn (vd. N3-05/NP/19-23); null với nguồn khác. */
    private String sourceRef;
    /** {@link com.kanjimastery.backend.model.ExamQuestionFlag}; null = không có cảnh báo. */
    private ExamQuestionFlag flag;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private List<AdminExamQuestionResponse> questions;
}
