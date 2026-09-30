package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** Trả lại trạng thái đang làm dở - phục vụ Frontend khôi phục UI khi F5 giữa giờ thi. */
@Getter
@Builder
@AllArgsConstructor
public class ExamSessionResponse {
    private Long attemptId;
    private long remainingSeconds;
    private Map<Long, String> answers;
}
