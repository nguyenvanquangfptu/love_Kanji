package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.SchedulerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class LearningProfileResponse {
    /** Người học đã đặt mục tiêu chưa (chưa thì các trường khác là giá trị mặc định). */
    private boolean configured;
    private String targetLevel;
    private LocalDate examDate;
    private int dailyMinutes;
    /** null = để app tính. */
    private Integer newWordsPerDay;
    /** SM2 hoặc FSRS. */
    private SchedulerType scheduler;
    /** Tỉ lệ nhớ mong muốn khi dùng FSRS. */
    private double desiredRetention;
    /** Mô hình trí nhớ FSRS của người học: tham số chung hay đã tối ưu riêng. */
    private FsrsParametersResponse fsrs;
}
