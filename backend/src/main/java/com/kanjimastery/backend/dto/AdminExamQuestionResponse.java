package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Một câu thi trên trang duyệt: đủ nội dung như trong đề, kèm đáp án, trạng thái duyệt và cảnh báo. */
@Getter
@Builder
@AllArgsConstructor
public class AdminExamQuestionResponse {
    private Long id;
    private String jlptLevel;
    /** {@link com.kanjimastery.backend.model.JlptQuestionType} */
    private String questionType;
    private String skill;
    /** {@link com.kanjimastery.backend.model.ExamQuestionStatus} */
    private String status;
    /** {@link com.kanjimastery.backend.model.ExamQuestionFlag}; null = không có cảnh báo. */
    private String flag;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    /** {@link com.kanjimastery.backend.model.ExamQuestionSource} */
    private String source;
    private String questionText;
    private String sentence;
    private String highlight;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption;
    private String explanation;
    /** Câu điền vào chỗ trống 【blankNo】 của đoạn văn passageId (文章の文法); null với câu đứng riêng. */
    private Long passageId;
    private Integer blankNo;
    /** Từ vựng câu hỏi kiểm tra. */
    private List<Word> words;
    /** Điểm ngữ pháp câu hỏi kiểm tra. */
    private List<Grammar> grammarPoints;

    public record Word(Long id, String character, String reading) {
    }

    /** Báo lỗi của người học đang chờ xem, cũ nhất trước. */
    private List<Report> reports;

    /** {@code reason}: {@link com.kanjimastery.backend.model.QuestionReportReason}. */
    public record Report(String reason, String note, LocalDateTime createdAt) {
    }

    public record Grammar(Long id, String pattern) {
    }
}
