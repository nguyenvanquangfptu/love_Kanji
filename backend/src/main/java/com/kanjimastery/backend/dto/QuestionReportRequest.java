package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Người học báo lỗi một câu hỏi đã làm. */
@Getter
@Setter
public class QuestionReportRequest {

    /** {@link com.kanjimastery.backend.model.QuestionReportReason} */
    @NotBlank(message = "Chưa chọn lý do")
    private String reason;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
