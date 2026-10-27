package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.model.FsrsState;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.SchedulerType;
import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.UserKanjiSrs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Bộ tính lịch dùng chung cho thẻ từ vựng và thẻ ngữ pháp - xem thêm các ca qua SrsService trong SrsServiceTest. */
class CardSchedulerTest {

    /** 20:00 ngày 23/10/2026 giờ Việt Nam. */
    private final StudyCalendar calendar = new StudyCalendar(new SrsProperties(),
            Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private final CardScheduler scheduler = new CardScheduler(new SrsCalculatorService(), calendar);
    private final LocalDateTime now = calendar.now();

    @Test
    void apply_shouldScheduleANewCardForTheNextStudyDay_andKeepBothAlgorithms() {
        UserKanjiSrs card = newCard();

        scheduler.apply(card, ReviewRating.GOOD, now, SchedulingSettings.DEFAULT);

        assertThat(card.getReviewIntervalDays()).isEqualTo(1);
        assertThat(card.getNextReviewAt()).isEqualTo(calendar.startOf(calendar.today().plusDays(1)));
        assertThat(card.getLastReviewedAt()).isEqualTo(now);
        assertThat(card.getRepetitionCount()).isEqualTo(1);
        assertThat(card.getEasinessFactor()).isEqualByComparingTo("2.50");
        // Lịch theo SM-2 nhưng trí nhớ FSRS vẫn được tính, đổi thuật toán lúc nào cũng được.
        assertThat(card.getStability()).isNotNull();
        assertThat(card.getDifficulty()).isNotNull();
        assertThat(card.getLapseCount()).isZero();
    }

    @Test
    void apply_shouldCountOneLapse_andTakeEfOnce_whenACardIsForgottenTwiceTheSameDay() {
        UserKanjiSrs card = reviewedCard(3, "2.50", 10);

        scheduler.apply(card, ReviewRating.AGAIN, now, SchedulingSettings.DEFAULT);
        BigDecimal efAfterFirstLapse = card.getEasinessFactor();
        scheduler.apply(card, ReviewRating.AGAIN, now.plusMinutes(5), SchedulingSettings.DEFAULT);

        assertThat(card.getLapseCount()).isEqualTo(1);
        assertThat(card.getRepetitionCount()).isZero();
        assertThat(efAfterFirstLapse).isLessThan(new BigDecimal("2.50"));
        assertThat(card.getEasinessFactor()).isEqualByComparingTo(efAfterFirstLapse);
    }

    @Test
    void apply_shouldUseTheFsrsInterval_whenTheLearnerChoseFsrs() {
        UserKanjiSrs card = newCard();

        scheduler.apply(card, ReviewRating.GOOD, now,
                new SchedulingSettings(SchedulerType.FSRS, 0.9, Fsrs.withDefaults()));

        // "Nhớ" lần đầu: độ ổn định 2,3 ngày, giữ tỉ lệ nhớ 90% thì 2 ngày sau mới ôn; SM-2 vẫn tính song song.
        assertThat(card.getReviewIntervalDays()).isEqualTo(2);
        assertThat(card.getRepetitionCount()).isEqualTo(1);
    }

    @Test
    void recall_shouldBeNullForANewCard_andNinetyPercentAfterStabilityDays() {
        UserKanjiSrs card = reviewedCard(2, "2.50", 10);
        card.setFsrs(new FsrsState(10.0, 5.0));

        assertThat(scheduler.recall(newCard(), now, Fsrs.withDefaults())).isNull();
        assertThat(scheduler.recall(card, now, Fsrs.withDefaults())).isCloseTo(0.9, within(1e-9));
    }

    @Test
    void previewIntervals_shouldGiveOneIntervalPerRating_neverShorterForABetterRating() {
        assertThat(scheduler.previewIntervals(reviewedCard(3, "2.50", 10), now, SchedulingSettings.DEFAULT))
                .hasSize(ReviewRating.values().length)
                .isSorted();
    }

    private UserKanjiSrs newCard() {
        return UserKanjiSrs.builder().userId(1L).kanjiId(2L).nextReviewAt(now).build();
    }

    /** Thẻ đang ôn bình thường, ôn lần trước cách đây đúng {@code intervalDays} ngày (đúng hạn hôm nay). */
    private UserKanjiSrs reviewedCard(int repetitions, String ef, int intervalDays) {
        return UserKanjiSrs.builder().userId(1L).kanjiId(2L)
                .sm2(new Sm2State(repetitions, new BigDecimal(ef)))
                .reviewIntervalDays(intervalDays)
                .nextReviewAt(now)
                .lastReviewedAt(now.minusDays(intervalDays))
                .build();
    }
}
