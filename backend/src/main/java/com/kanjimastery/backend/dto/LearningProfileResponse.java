package com.kanjimastery.backend.dto;

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
}
