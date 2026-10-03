package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Sửa tiêu đề, nội dung đoạn văn; các chỗ trống 【n】 phải khớp với câu hỏi của đoạn. */
@Getter
@Setter
public class ExamPassageRequest {

    @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
    private String title;

    @NotBlank(message = "Thiếu nội dung đoạn văn")
    private String content;
}
