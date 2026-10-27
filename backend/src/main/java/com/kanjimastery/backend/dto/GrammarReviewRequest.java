package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Một lần ôn một điểm ngữ pháp: trả lời câu hỏi ({@code questionId} + {@code chosenOption}, server tự chấm) hoặc tự
 * chấm thẻ lật ({@code rating}).
 */
@Getter
@Setter
public class GrammarReviewRequest {

    @NotNull(message = "grammarPointId không được để trống")
    private Long grammarPointId;

    private Long questionId;

    @Pattern(regexp = "[A-Da-d]", message = "chosenOption phải là A, B, C hoặc D")
    private String chosenOption;

    /** Chỉ dùng với thẻ lật: 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ. */
    @Min(value = 1, message = "rating phải từ 1 đến 4")
    @Max(value = 4, message = "rating phải từ 1 đến 4")
    private Integer rating;

    /** Thời gian trả lời (mili giây), không bắt buộc. */
    private Integer responseMs;
}
