package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Đổi trạng thái duyệt của câu thi; {@code note} bắt buộc khi loại câu. */
@Getter
@Setter
public class ExamQuestionStatusRequest {

    /** {@link com.kanjimastery.backend.model.ExamQuestionStatus} */
    @NotBlank(message = "Thiếu trạng thái")
    private String status;

    private String note;
}
