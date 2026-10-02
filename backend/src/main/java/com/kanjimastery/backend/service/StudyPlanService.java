package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.DailyPlanResponse;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Kế hoạch ôn mỗi ngày vừa với thời gian người học có: ôn bao nhiêu thẻ, học bao nhiêu từ mới. Từ mới chỉ được
 * thêm khi còn chỗ sau phần ôn, vì mỗi từ mới kéo theo các lượt ôn những ngày sau.
 */
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    static final int DEFAULT_SECONDS_PER_CARD = 8;
    static final int PACE_SAMPLES = 300;
    /** Ít khoảng cách giữa hai lần chấm thẻ hơn chừng này thì chưa tin nhịp ôn đo được. */
    static final int MIN_PACE_SAMPLES = 20;
    /** Nạp n từ mới mỗi ngày thì khi vào nhịp, mỗi ngày có thêm khoảng 4n lượt ôn (ngày học, rồi sau 1, 6, 15 ngày). */
    static final int REVIEWS_PER_NEW_WORD = 4;
    static final int FORECAST_DAYS = 7;

    private final UserKanjiSrsRepository srsRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final SrsProperties srsProperties;
    private final StudyCalendar calendar;

    @Transactional(readOnly = true)
    public DailyPlanResponse today(Long userId) {
        LocalDateTime now = calendar.now();
        int dailyMinutes = srsProperties.getDefaultDailyMinutes();
        int secondsPerCard = secondsPerCard(userId);
        int capacity = Math.max(1, dailyMinutes * 60 / secondsPerCard);

        long dueReviews = srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(userId, now);
        double upcomingPerDay = srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtBetween(
                userId, now, now.plusDays(FORECAST_DAYS)) / (double) FORECAST_DAYS;
        int reviewsToday = (int) Math.min(dueReviews, capacity);

        int wanted = srsProperties.getDefaultNewWordsPerDay();
        // Chỗ còn lại sau phần ôn nặng hơn trong hai mức: hôm nay, hoặc trung bình tuần tới.
        int roomForNewWords = (int) (Math.max(0, capacity - Math.max(dueReviews, upcomingPerDay)) / REVIEWS_PER_NEW_WORD);
        int newPerDay = Math.min(wanted, roomForNewWords);
        long newLearnedToday = reviewLogRepository.countNewWordsLearnedSince(userId, calendar.startOfToday());
        long newWaiting = srsRepository.countByUserIdAndLastReviewedAtIsNull(userId);
        int newToday = (int) Math.min(Math.max(0, newPerDay - newLearnedToday), newWaiting);

        return DailyPlanResponse.builder()
                .dailyMinutes(dailyMinutes)
                .secondsPerCard(secondsPerCard)
                .reviewCapacity(capacity)
                .dueReviews(dueReviews)
                .reviewsToday(reviewsToday)
                .newPerDay(newPerDay)
                .newPerDayLimitedByTime(newPerDay < wanted)
                .newLearnedToday(newLearnedToday)
                .newWaiting(newWaiting)
                .newToday(newToday)
                .estimatedMinutes((int) Math.ceil((reviewsToday + newToday) * secondsPerCard / 60.0))
                .build();
    }

    /** Số giây cho một thẻ theo nhịp ôn thật (3-60 giây); chưa đủ dữ liệu thì {@value #DEFAULT_SECONDS_PER_CARD} giây. */
    int secondsPerCard(Long userId) {
        ResponseTimeStats pace = reviewLogRepository.flashcardPace(userId, PACE_SAMPLES);
        if (pace == null || pace.getMedianMs() == null || pace.getSamples() < MIN_PACE_SAMPLES) {
            return DEFAULT_SECONDS_PER_CARD;
        }
        return (int) Math.min(60, Math.max(3, Math.round(pace.getMedianMs() / 1000.0)));
    }
}
