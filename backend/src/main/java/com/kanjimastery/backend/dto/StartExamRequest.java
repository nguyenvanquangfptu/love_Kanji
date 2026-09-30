package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartExamRequest {

    @NotBlank(message = "jlptLevel không được để trống")
    private String jlptLevel;

    @Min(value = 1, message = "questionCount phải >= 1")
    @Max(value = 100, message = "questionCount phải <= 100")
    private Integer questionCount;
}
