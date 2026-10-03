package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "Thiếu dạng câu")
    private String type;

    @Min(value = 1, message = "Ít nhất 1 câu")
    @Max(value = 8, message = "Tối đa 8 câu mỗi lần")
    private int count = 5;
}
