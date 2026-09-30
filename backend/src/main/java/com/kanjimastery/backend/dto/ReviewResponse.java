package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ReviewResponse {
    private Long kanjiId;
    private Integer repetitionCount;
    private BigDecimal easinessFactor;
    private Integer reviewIntervalDays;
    private LocalDateTime nextReviewAt;
}
