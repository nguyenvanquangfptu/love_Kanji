package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.UserKanjiSrs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DailySessionOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 20, 0);

    @Test
    void order_shouldPutTheReviewsMostLikelyForgottenFirst() {
        UserKanjiSrs longIntervalLate = review(1L, NOW.minusDays(2), 30);  // trễ 2/30 khoảng ôn
        UserKanjiSrs shortIntervalLate = review(2L, NOW.minusDays(2), 1);  // trễ 2 khoảng ôn
        UserKanjiSrs justDue = review(3L, NOW.minusMinutes(5), 6);

        List<UserKanjiSrs> session = DailySessionOrder.order(List.of(longIntervalLate, justDue, shortIntervalLate),
                NOW, 10, 10);

        assertThat(session).extracting(UserKanjiSrs::getKanjiId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void order_shouldSlipOneNewWordInAfterEveryFourReviews_andAppendTheRest() {
        List<UserKanjiSrs> due = Stream.concat(
                LongStream.rangeClosed(1, 8).mapToObj(id -> review(id, NOW.minusDays(1), 1)),
                LongStream.rangeClosed(101, 104).mapToObj(id -> newWord(id, NOW.minusHours(1)))).toList();

        List<UserKanjiSrs> session = DailySessionOrder.order(due, NOW, 10, 10);

        assertThat(session).extracting(UserKanjiSrs::getKanjiId)
                .containsExactly(1L, 2L, 3L, 4L, 101L, 5L, 6L, 7L, 8L, 102L, 103L, 104L);
    }

    @Test
    void order_shouldRespectTheLimits_andTakeNewWordsInTheOrderTheyWereAdded() {
        List<UserKanjiSrs> due = List.of(
                review(1L, NOW.minusDays(3), 1), review(2L, NOW.minusDays(2), 1), review(3L, NOW.minusDays(1), 1),
                newWord(12L, NOW.minusDays(1)), newWord(11L, NOW.minusDays(1)), newWord(10L, NOW.minusHours(1)));

        List<UserKanjiSrs> session = DailySessionOrder.order(due, NOW, 2, 2);

        // Hai thẻ trễ nhất; hai từ mới được thêm sớm nhất (cùng lúc thì theo id từ).
        assertThat(session).extracting(UserKanjiSrs::getKanjiId).containsExactly(1L, 2L, 11L, 12L);
    }

    @Test
    void overdueRatio_shouldBeZero_beforeTheCardIsDue() {
        assertThat(DailySessionOrder.overdueRatio(review(1L, NOW.plusDays(1), 6), NOW)).isZero();
        assertThat(DailySessionOrder.overdueRatio(review(1L, NOW.minusDays(3), 6), NOW)).isEqualTo(0.5);
    }

    private static UserKanjiSrs review(long kanjiId, LocalDateTime nextReviewAt, int intervalDays) {
        return UserKanjiSrs.builder().kanjiId(kanjiId).sm2(new Sm2State(2, new BigDecimal("2.50")))
                .reviewIntervalDays(intervalDays).nextReviewAt(nextReviewAt)
                .lastReviewedAt(nextReviewAt.minusDays(intervalDays)).build();
    }

    private static UserKanjiSrs newWord(long kanjiId, LocalDateTime addedAt) {
        return UserKanjiSrs.builder().kanjiId(kanjiId).sm2(new Sm2State(0, new BigDecimal("2.50")))
                .reviewIntervalDays(0).nextReviewAt(addedAt).build();
    }
}
