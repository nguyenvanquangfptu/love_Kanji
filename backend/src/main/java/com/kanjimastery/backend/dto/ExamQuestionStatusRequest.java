package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamQuestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Đổi trạng thái duyệt của câu thi; {@code note} bắt buộc khi loại câu. */
@Getter
@Setter
public class ExamQuestionStatusRequest {

    @NotNull(message = "Thiếu trạng thái")
    private ExamQuestionStatus status;

    private String note;
}
