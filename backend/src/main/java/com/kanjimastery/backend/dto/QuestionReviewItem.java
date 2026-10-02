package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

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
}
