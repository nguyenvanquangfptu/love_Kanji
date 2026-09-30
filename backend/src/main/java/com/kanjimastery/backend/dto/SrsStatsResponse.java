package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SrsStatsResponse {
    private long totalCardsStarted;
    private long dueForReview;      // "Cần ôn tập gấp"
    private long stillLearning;     // "Đang học dở"
    private long deeplyMemorized;   // "Đã ghi nhớ sâu" (interval >= 21 ngày)
}
