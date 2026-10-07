package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.DailyPlanResponse;
import com.kanjimastery.backend.model.LearningProfile;
import com.kanjimastery.backend.repository.LearningProfileRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import com.kanjimastery.backend.repository.TagRepository;
import com.kanjimastery.backend.repository.TagRepository.LessonToAdd;
import com.kanjimastery.backend.repository.TagRepository.ScopeProgress;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {

    private static final Long USER_ID = 7L;
    /** 10:00 UTC = 17:00 ở Việt Nam: ngày học 02/10, bắt đầu 21:00 UTC ngày 01/10. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final LocalDateTime START_OF_TODAY = LocalDateTime.of(2026, 10, 1, 21, 0);

    @Mock
    private UserKanjiSrsRepository srsRepository;
    @Mock
    private ReviewLogRepository reviewLogRepository;
    @Mock
    private LearningProfileRepository profileRepository;
    @Mock
    private TagRepository tagRepository;

    private StudyPlanService service;

    @BeforeEach
    void setUp() {
        SrsProperties properties = new SrsProperties();
        StudyCalendar calendar = new StudyCalendar(properties, Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC));
        service = new StudyPlanService(srsRepository, reviewLogRepository, profileRepository, tagRepository, properties,
                calendar);
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
        assertThat(plan.getNewPerDaySource()).isEqualTo(StudyPlanService.SOURCE_DEFAULT);
        assertThat(plan.isNewPerDayLimitedByTime()).isFalse();
        assertThat(plan.getNewToday()).isEqualTo(7);
        assertThat(plan.getEstimatedMinutes()).isEqualTo(5);
        assertThat(plan.isGoalSet()).isFalse();
        assertThat(plan.getNextLesson()).isNull();
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
        givenLoad(60, 0, 0, 30);

        DailyPlanResponse plan = service.today(USER_ID);

        assertThat(plan.getSecondsPerCard()).isEqualTo(12);
        assertThat(plan.getReviewCapacity()).isEqualTo(100);
        assertThat(plan.getNewPerDay()).isEqualTo(10);
    }

    @Test
    void today_shouldWaitForEnoughSamples_beforeTrustingThePace() {
        givenPace(30_000.0, 19);
        givenLoad(0, 0, 0, 30);

        assertThat(service.today(USER_ID).getSecondsPerCard()).isEqualTo(StudyPlanService.DEFAULT_SECONDS_PER_CARD);
    }

    @Test
    void today_shouldSpreadTheRemainingWordsUntilTwoWeeksBeforeTheExam_andForecastFromTheRecentPace() {
        givenPace(null, 0);
        givenProfile(JlptLevel.N4, TODAY.plusDays(60), 30, null);
        givenScope(List.of("N5", "N4"), 1697, 197);
        givenLoad(25, 0, 3, 100);
        // Học từ 20 ngày trước: nhịp tính trên 14 ngày gần nhất, 280 từ = 20 từ/ngày.
        when(reviewLogRepository.firstReviewAt(USER_ID)).thenReturn(Optional.of(START_OF_TODAY.minusDays(20)));
        when(reviewLogRepository.countNewWordsLearnedSince(USER_ID, START_OF_TODAY.minusDays(13))).thenReturn(280L);

        DailyPlanResponse plan = service.today(USER_ID);

        // 1500 từ trong 60 - 14 = 46 ngày: 33 từ/ngày; 30 phút đủ 225 thẻ nên không bị cắt.
        assertThat(plan.isGoalSet()).isTrue();
        assertThat(plan.getWordsToLearn()).isEqualTo(1500);
        assertThat(plan.getNewPerDaySource()).isEqualTo(StudyPlanService.SOURCE_EXAM);
        assertThat(plan.getNewPerDayWanted()).isEqualTo(33);
        assertThat(plan.getNewPerDay()).isEqualTo(33);
        assertThat(plan.getRecentNewPerDay()).isEqualTo(20.0);
        // Giữ 20 từ/ngày thì cần 75 ngày - trễ so với mốc 46 ngày.
        assertThat(plan.getProjectedFinish()).isEqualTo(TODAY.plusDays(75));
        assertThat(plan.getOnTrack()).isFalse();
    }

    @Test
    void today_shouldNotForecast_fromLessThanAWeekOfHistory() {
        givenPace(null, 0);
        givenProfile(JlptLevel.N4, TODAY.plusDays(60), 30, null);
        givenScope(List.of("N5", "N4"), 1697, 197);
        givenLoad(0, 0, 1, 100);
        // Mới dùng app từ hôm qua: 2 ngày, chưa đủ để nói lên nhịp học.
        when(reviewLogRepository.firstReviewAt(USER_ID)).thenReturn(Optional.of(START_OF_TODAY.minusHours(10)));

        DailyPlanResponse plan = service.today(USER_ID);

        assertThat(plan.getRecentNewPerDay()).isZero();
        assertThat(plan.getProjectedFinish()).isNull();
        assertThat(plan.getOnTrack()).isNull();
        assertThat(plan.getNewPerDayWanted()).isEqualTo(33);
    }

    @Test
    void today_shouldWarn_whenTheDailyTimeCannotFitTheWordsNeededForTheExam() {
        givenPace(null, 0);
        givenProfile(JlptLevel.N4, TODAY.plusDays(20), 10, null);
        givenScope(List.of("N5", "N4"), 1000, 0);
        givenLoad(40, 0, 0, 200);
        when(reviewLogRepository.firstReviewAt(USER_ID)).thenReturn(Optional.empty());

        DailyPlanResponse plan = service.today(USER_ID);

        // Cần 1000 / 6 = 167 từ/ngày, nhưng 10 phút (75 thẻ) chỉ còn chỗ cho (75 - 40) / 4 = 8 từ.
        assertThat(plan.getNewPerDayWanted()).isEqualTo(167);
        assertThat(plan.getNewPerDay()).isEqualTo(8);
        assertThat(plan.isNewPerDayLimitedByTime()).isTrue();
        assertThat(plan.getProjectedFinish()).isNull();
        assertThat(plan.getOnTrack()).isNull();
    }

    @Test
    void today_shouldKeepTheLearnersOwnNumberOfNewWords_evenWhenBusy() {
        givenPace(null, 0);
        givenProfile(null, null, 20, 30);
        givenLoad(400, 0, 0, 100);

        DailyPlanResponse plan = service.today(USER_ID);

        assertThat(plan.getNewPerDaySource()).isEqualTo(StudyPlanService.SOURCE_CUSTOM);
        assertThat(plan.getNewPerDay()).isEqualTo(30);
        assertThat(plan.isNewPerDayLimitedByTime()).isFalse();
        assertThat(plan.getWordsToLearn()).isNull();
    }

    @Test
    void today_shouldSuggestTheNextLesson_whenTooFewNewWordsAreWaiting() {
        givenPace(null, 0);
        givenLoad(0, 0, 0, 4);
        LessonToAdd lesson = new LessonToAdd() {
            @Override
            public Long getTagId() {
                return 64L;
            }

            @Override
            public String getName() {
                return "N5-02";
            }

            @Override
            public long getWords() {
                return 31;
            }
        };
        when(tagRepository.nextLessonToAdd(USER_ID, List.of("N5", "N4", "N3", "N2", "N1"))).thenReturn(Optional.of(lesson));

        DailyPlanResponse plan = service.today(USER_ID);

        assertThat(plan.getNextLesson()).isNotNull();
        assertThat(plan.getNextLesson().getName()).isEqualTo("N5-02");
        assertThat(plan.getNextLesson().getWords()).isEqualTo(31);
    }

    @Test
    void levelsUpTo_shouldIncludeEveryEasierLevel() {
        assertThat(StudyPlanService.levelsUpTo(JlptLevel.N5)).containsExactly("N5");
        assertThat(StudyPlanService.levelsUpTo(JlptLevel.N3)).containsExactly("N5", "N4", "N3");
        assertThat(StudyPlanService.levelsUpTo(null)).containsExactly("N5", "N4", "N3", "N2", "N1");
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

    private void givenProfile(JlptLevel targetLevel, LocalDate examDate, int dailyMinutes, Integer newWordsPerDay) {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(LearningProfile.builder()
                .userId(USER_ID)
                .targetLevel(targetLevel)
                .examDate(examDate)
                .dailyMinutes(dailyMinutes)
                .newWordsPerDay(newWordsPerDay)
                .build()));
    }

    private void givenScope(List<String> levels, long total, long started) {
        when(tagRepository.scopeProgress(USER_ID, levels)).thenReturn(new ScopeProgress() {
            @Override
            public long getTotal() {
                return total;
            }

            @Override
            public long getStarted() {
                return started;
            }
        });
    }

    /** {@code upcoming7Days}: thẻ đã học sẽ đến hạn trong 7 ngày tới. */
    private void givenLoad(long dueReviews, long upcoming7Days, long newLearnedToday, long newWaiting) {
        when(srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(eq(USER_ID), any()))
                .thenReturn(dueReviews);
        when(srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(upcoming7Days);
        when(reviewLogRepository.countNewWordsLearnedSince(USER_ID, START_OF_TODAY)).thenReturn(newLearnedToday);
        when(srsRepository.countByUserIdAndLastReviewedAtIsNull(USER_ID)).thenReturn(newWaiting);
    }
}
