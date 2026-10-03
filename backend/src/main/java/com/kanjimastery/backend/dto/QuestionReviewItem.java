package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class QuestionReviewItem {
    private Long questionId;
    private String questionText;
    /** Câu ví dụ kiểu đề JLPT, gạch chân {@code highlight}; null nếu không có. */
    private String sentence;
    private String highlight;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption;
    private String selectedOption;
    private boolean correct;
    private String explanation;
    /** Kỹ năng câu hỏi kiểm tra; null nếu chưa phân loại. */
    private String skill;
    /** Dạng câu JLPT ({@link com.kanjimastery.backend.model.JlptQuestionType}); null với câu chỉ dùng cho thi nhanh. */
    private String questionType;
    /** Câu điền vào chỗ trống 【blankNo】 của đoạn văn passageId (文章の文法); null với câu đứng riêng. */
    private Long passageId;
    private Integer blankNo;
    /** Các điểm ngữ pháp câu này kiểm tra; rỗng với câu từ vựng. */
    private List<Grammar> grammarPoints;
    /** Người học đang xem đã báo lỗi câu này. */
    private boolean reported;

    public record Grammar(Long id, String pattern, String meaningVi) {
    }
}
