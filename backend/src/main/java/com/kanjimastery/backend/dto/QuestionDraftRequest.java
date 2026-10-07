package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptQuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Nhờ AI viết nháp câu thi cho một điểm ngữ pháp. */
@Getter
@Setter
public class QuestionDraftRequest {

    @NotNull(message = "Thiếu điểm ngữ pháp")
    private Long grammarPointId;

    /** GRAMMAR_FORM hoặc SENTENCE_ORDER. */
    @NotNull(message = "Thiếu dạng câu")
    private JlptQuestionType type;

    @Min(value = 1, message = "Ít nhất 1 câu")
    @Max(value = 8, message = "Tối đa 8 câu mỗi lần")
    private int count = 5;
}
