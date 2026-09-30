package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.dto.KanjiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class DailyCardResponse {
    private Long srsId;
    private KanjiResponse kanji;
    private Integer repetitionCount;
    private BigDecimal easinessFactor;
    private Integer reviewIntervalDays;
    private LocalDateTime nextReviewAt;
    private LocalDateTime lastReviewedAt;
}
