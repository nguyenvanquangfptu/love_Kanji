package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StartJlptExamRequest {

    @NotBlank(message = "jlptLevel không được để trống")
    private String jlptLevel;

    /** Các phần muốn làm ({@link com.kanjimastery.backend.model.ExamSection}); luôn làm theo thứ tự của đề thật. */
    @NotEmpty(message = "Chọn ít nhất một phần thi")
    private List<String> sections;
}
