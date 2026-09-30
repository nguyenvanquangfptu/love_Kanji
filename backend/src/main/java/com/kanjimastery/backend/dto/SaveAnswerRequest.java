package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveAnswerRequest {

    @NotNull(message = "questionId không được để trống")
    private Long questionId;

    @Pattern(regexp = "[A-Da-d]", message = "selectedOption phải là A, B, C hoặc D")
    private String selectedOption;
}
