package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptQuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Nhờ AI viết nháp câu từ vựng cho các từ trong bài của một cấp độ chưa có câu dạng đó. */
@Getter
@Setter
public class VocabularyDraftRequest {

    @NotBlank(message = "Thiếu cấp độ")
    private String level;

    /** PARAPHRASE hoặc USAGE. */
    @NotNull(message = "Thiếu dạng câu")
    private JlptQuestionType type;

    @Min(value = 1, message = "Ít nhất 1 câu")
    @Max(value = 8, message = "Tối đa 8 câu mỗi lần")
    private int count = 5;
}
