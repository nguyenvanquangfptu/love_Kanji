package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AddSrsCardsResponse {
    private int added;            // Từ mới vào lịch ôn, đến hạn ôn ngay
    private int alreadyInReview;  // Từ đã có trong lịch ôn từ trước - giữ nguyên tiến độ
}
