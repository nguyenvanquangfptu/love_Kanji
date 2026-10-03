package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Sửa nội dung một câu thi trên trang duyệt. */
@Getter
@Setter
public class AdminExamQuestionRequest {

    @NotBlank(message = "Thiếu nội dung câu hỏi")
    private String questionText;

    private String sentence;

    @Size(max = 100, message = "Phần gạch chân tối đa 100 ký tự")
    private String highlight;

    @NotBlank(message = "Thiếu lựa chọn A")
    private String optionA;
    @NotBlank(message = "Thiếu lựa chọn B")
    private String optionB;
    @NotBlank(message = "Thiếu lựa chọn C")
    private String optionC;
    @NotBlank(message = "Thiếu lựa chọn D")
    private String optionD;

    @NotBlank(message = "Thiếu đáp án")
    @Pattern(regexp = "[A-D]", message = "Đáp án phải là A, B, C hoặc D")
    private String correctOption;

    private String explanation;

    /** Các điểm ngữ pháp câu kiểm tra; null = giữ nguyên. */
    private List<Long> grammarPointIds;
}
