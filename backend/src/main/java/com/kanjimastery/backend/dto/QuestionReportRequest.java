package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.QuestionReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Người học báo lỗi một câu hỏi đã làm. */
@Getter
@Setter
public class QuestionReportRequest {

    @NotNull(message = "Chưa chọn lý do")
    private QuestionReportReason reason;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
