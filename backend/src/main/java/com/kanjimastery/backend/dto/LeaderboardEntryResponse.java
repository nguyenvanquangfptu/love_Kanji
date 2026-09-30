package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LeaderboardEntryResponse {
    private int rank;
    private Long userId;
    private String username;
    private double score;
}
