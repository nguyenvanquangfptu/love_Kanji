package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SrsTagStatusResponse {
    private long totalWords;  // Số từ trong bài (tag)
    private long inReview;    // Số từ của bài đã nằm trong lịch ôn của người dùng
}
