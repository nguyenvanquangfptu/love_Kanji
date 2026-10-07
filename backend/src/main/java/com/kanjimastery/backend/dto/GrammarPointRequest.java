package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrammarPointRequest {

    @NotBlank(message = "jlptLevel không được để trống")
    @Pattern(regexp = "N[1-5]", message = "jlptLevel phải là N1-N5")
    private String jlptLevel;

    @Size(max = 20, message = "Tên bài tối đa 20 ký tự")
    private String lesson;

    @NotBlank(message = "Mẫu ngữ pháp không được để trống")
    @Size(max = 100, message = "Mẫu ngữ pháp tối đa 100 ký tự")
    private String pattern;

    @Size(max = 200, message = "Cách nối tối đa 200 ký tự")
    private String connection;

    @NotBlank(message = "Nghĩa không được để trống")
    private String meaningVi;

    private String explanationVi;
}
