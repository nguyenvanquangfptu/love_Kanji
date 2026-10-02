package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Câu hỏi hiển thị cho thí sinh trong lúc làm bài - KHÔNG lộ đáp án đúng/giải thích. */
@Getter
@Builder
@AllArgsConstructor
public class ExamQuestionPublicResponse {
    private Long id;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    /** Câu ví dụ kiểu đề JLPT, gạch chân {@code highlight}; null nếu không có. */
    private String sentence;
    private String highlight;
    /** Dạng câu JLPT ({@link com.kanjimastery.backend.model.JlptQuestionType}); null với câu chỉ dùng cho thi nhanh. */
    private String questionType;

    public static ExamQuestionPublicResponse from(ExamQuestion q) {
        return ExamQuestionPublicResponse.builder()
                .id(q.getId())
                .questionText(q.getQuestionText())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .sentence(q.getSentence())
                .highlight(q.getHighlight())
                .questionType(q.getQuestionType())
                .build();
    }
}
