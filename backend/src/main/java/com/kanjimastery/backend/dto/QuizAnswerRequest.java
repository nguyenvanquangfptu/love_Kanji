package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.QuizDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizAnswerRequest {

    @NotNull(message = "kanjiId không được để trống")
    private Long kanjiId;

    @NotNull(message = "direction không được để trống")
    private QuizDirection direction;

    @NotBlank(message = "chosenAnswer không được để trống")
    @Size(max = 1000, message = "chosenAnswer quá dài")
    private String chosenAnswer;

    /** Thời gian từ lúc hiện câu hỏi tới lúc chọn đáp án (mili giây), không bắt buộc. */
    private Integer responseMs;
}
