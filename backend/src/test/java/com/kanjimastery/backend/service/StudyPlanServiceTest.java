package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.DailyPlanResponse;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserKanjiSrsRepository srsRepository;
    @Mock
    private ReviewLogRepository reviewLogRepository;

    private StudyPlanService service;

    @BeforeEach
    void setUp() {
        SrsProperties properties = new SrsProperties();
        StudyCalendar calendar = new StudyCalendar(properties, Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC));
        service = new StudyPlanService(srsRepository, reviewLogRepository, properties, calendar);
    }

    @Test
    void today_shouldPlanAllDueReviewsAndTheDefaultNewWords_forALightLoad() {
        givenPace(null, 0);
        givenLoad(25, 70, 3, 40);

        DailyPlanResponse plan = service.today(USER_ID);

        // 20 phút x 60 / 8 giây = 150 thẻ; còn (150 - 25) / 4 = 31 chỗ cho từ mới, mặc định chỉ lấy 10.
        assertThat(plan.getReviewCapacity()).isEqualTo(150);
        assertThat(plan.getReviewsToday()).isEqualTo(25);
        assertThat(plan.getNewPerDay()).isEqualTo(10);
        assertThat(plan.isNewPerDayLimitedByTime()).isFalse();
        assertThat(plan.getNewToday()).isEqualTo(7);
        assertThat(plan.getEstimatedMinutes()).isEqualTo(5);
    }

    @Test
    void today_shouldCapReviewsAndHoldNewWordsBack_afterALongBreak() {
        givenPace(null, 0);
        givenLoad(400, 210, 0, 40);

        DailyPlanResponse plan = service.today(USER_ID);

        assertThat(plan.getDueReviews()).isEqualTo(400);
        assertThat(plan.getReviewsToday()).isEqualTo(150);
        assertThat(plan.getNewPerDay()).isZero();
        assertThat(plan.isNewPerDayLimitedByTime()).isTrue();
        assertThat(plan.getNewToday()).isZero();
    }

    @Test
    void today_shouldUseTheLearnersOwnPace_onceThereIsEnoughOfIt() {
        // Mỗi thẻ mất khoảng 12 giây: 20 phút chỉ đủ 100 thẻ.
        givenPace(12_000.0, 20);
        givenLoad(60, 0, 0, 0);

        DailyPlanResponse plan = service.today(USER_ID);

        assertThat(plan.getSecondsPerCard()).isEqualTo(12);
        assertThat(plan.getReviewCapacity()).isEqualTo(100);
        assertThat(plan.getNewPerDay()).isEqualTo(10);
        assertThat(plan.getNewToday()).isZero();
    }

    @Test
    void today_shouldWaitForEnoughSamples_beforeTrustingThePace() {
        givenPace(30_000.0, 19);
        givenLoad(0, 0, 0, 0);

        assertThat(service.today(USER_ID).getSecondsPerCard()).isEqualTo(StudyPlanService.DEFAULT_SECONDS_PER_CARD);
    }

    private void givenPace(Double medianMs, long samples) {
        when(reviewLogRepository.flashcardPace(eq(USER_ID), anyInt())).thenReturn(new ResponseTimeStats() {
            @Override
            public Double getMedianMs() {
                return medianMs;
            }

            @Override
            public long getSamples() {
                return samples;
            }
        });
    }

    /** {@code upcoming7Days}: thẻ đã học sẽ đến hạn trong 7 ngày tới. */
    private void givenLoad(long dueReviews, long upcoming7Days, long newLearnedToday, long newWaiting) {
        when(srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(eq(USER_ID), any()))
                .thenReturn(dueReviews);
        when(srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(upcoming7Days);
        when(reviewLogRepository.countNewWordsLearnedSince(eq(USER_ID), any())).thenReturn(newLearnedToday);
        when(srsRepository.countByUserIdAndLastReviewedAtIsNull(USER_ID)).thenReturn(newWaiting);
    }
}
