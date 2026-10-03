package com.kanjimastery.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardServiceTest {

    @Test
    void jlptRankingScore_shouldRankByAccuracyFirst_thenBySpeed() {
        // 30/40 đúng (75%) chậm vẫn xếp trên 29/40 nhanh.
        assertThat(LeaderboardService.jlptRankingScore(30, 40, 3000))
                .isGreaterThan(LeaderboardService.jlptRankingScore(29, 40, 600));
        // Cùng tỉ lệ đúng (đề có số câu khác nhau): ai nhanh hơn xếp trên.
        assertThat(LeaderboardService.jlptRankingScore(15, 20, 1200))
                .isGreaterThan(LeaderboardService.jlptRankingScore(30, 40, 1800));
        // Làm cả ngày cũng không bằng điểm của tỉ lệ đúng cao hơn một bậc.
        assertThat(LeaderboardService.jlptRankingScore(1, 10_000, 1_000_000))
                .isGreaterThan(LeaderboardService.jlptRankingScore(0, 10_000, 0));
    }
}
