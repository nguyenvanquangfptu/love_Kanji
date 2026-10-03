package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Nhờ AI viết nháp câu từ vựng cho các từ trong bài của một cấp độ chưa có câu dạng đó. */
@Getter
@Setter
public class VocabularyDraftRequest {

    @NotBlank(message = "Thiếu cấp độ")
    private String level;

    /** PARAPHRASE hoặc USAGE. */
    @NotBlank(message = "Thiếu dạng câu")
    private String type;

    @Min(value = 1, message = "Ít nhất 1 câu")
    @Max(value = 8, message = "Tối đa 8 câu mỗi lần")
    private int count = 5;
}
