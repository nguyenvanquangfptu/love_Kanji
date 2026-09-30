package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MyRankResponse {
    private Long userId;
    /** null nếu user chưa có điểm nào ở cấp độ này. */
    private Integer rank;
    private Double score;
}
