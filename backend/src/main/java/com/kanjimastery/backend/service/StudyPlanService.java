package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.DailyPlanResponse;
import com.kanjimastery.backend.model.LearningProfile;
import com.kanjimastery.backend.repository.LearningProfileRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import com.kanjimastery.backend.repository.TagRepository;
import com.kanjimastery.backend.repository.TagRepository.ScopeProgress;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Kế hoạch ôn mỗi ngày vừa với thời gian người học có: ôn bao nhiêu thẻ, học bao nhiêu từ mới. Từ mới chỉ được
 * thêm khi còn chỗ sau phần ôn, vì mỗi từ mới kéo theo các lượt ôn những ngày sau. Có mục tiêu (cấp độ + ngày thi)
 * thì số từ mới mỗi ngày tính sao cho học xong trước ngày thi, kèm dự báo theo nhịp học thật.
 */
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    public static final String SOURCE_DEFAULT = "DEFAULT";
    public static final String SOURCE_CUSTOM = "CUSTOM";
    public static final String SOURCE_EXAM = "EXAM";

    static final int DEFAULT_SECONDS_PER_CARD = 8;
    static final int PACE_SAMPLES = 300;
    /** Ít khoảng cách giữa hai lần chấm thẻ hơn chừng này thì chưa tin nhịp ôn đo được. */
    static final int MIN_PACE_SAMPLES = 20;
    /** Nạp n từ mới mỗi ngày thì khi vào nhịp, mỗi ngày có thêm khoảng 4n lượt ôn (ngày học, rồi sau 1, 6, 15 ngày). */
    static final int REVIEWS_PER_NEW_WORD = 4;
    static final int FORECAST_DAYS = 7;
    /** Học xong từ mới trước ngày thi chừng này ngày để còn ôn tổng. */
    static final int REVISION_DAYS_BEFORE_EXAM = 14;
    /** Nhịp học từ mới tính trên chừng này ngày gần nhất. */
    static final int PACE_WINDOW_DAYS = 14;
    /** Dùng app chưa đủ chừng này ngày thì nhịp học còn quá nhiễu để dự báo ngày học xong. */
    static final int MIN_PACE_DAYS = 7;
    /** Từ dễ đến khó: mục tiêu N4 gồm cả từ của các bài N5. */

    private final UserKanjiSrsRepository srsRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final LearningProfileRepository profileRepository;
    private final TagRepository tagRepository;
    private final SrsProperties srsProperties;
    private final StudyCalendar calendar;

    @Transactional(readOnly = true)
    public DailyPlanResponse today(Long userId) {
        LocalDateTime now = calendar.now();
        LocalDate today = calendar.today();
        LearningProfile profile = profileRepository.findById(userId).orElse(null);
        JlptLevel targetLevel = profile == null ? null : profile.getTargetLevel();
        LocalDate examDate = profile == null ? null : profile.getExamDate();

        int dailyMinutes = profile != null ? profile.getDailyMinutes() : srsProperties.getDefaultDailyMinutes();
        int secondsPerCard = secondsPerCard(userId);
        int capacity = Math.max(1, dailyMinutes * 60 / secondsPerCard);

        long dueReviews = srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(userId, now);
        double upcomingPerDay = srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtBetween(
                userId, now, now.plusDays(FORECAST_DAYS)) / (double) FORECAST_DAYS;
        // Lượt ôn đã làm hôm nay đã dùng một phần thời gian của ngày: kế hoạch chỉ còn phần còn lại. Không trừ thì ôn
        // xong phần của hôm nay, mở lại trang lại có thêm một phiên đầy đủ, và giới hạn theo thời gian không bao giờ dừng.
        long reviewedToday = reviewLogRepository.countReviewsSince(userId, calendar.startOfToday());
        int reviewsToday = (int) Math.min(dueReviews, Math.max(0, capacity - reviewedToday));

        Long wordsToLearn = null;
        if (targetLevel != null) {
            ScopeProgress progress = tagRepository.scopeProgress(userId, levelsUpTo(targetLevel));
            wordsToLearn = progress.getTotal() - progress.getStarted();
        }

        int wanted;
        String source;
        if (profile != null && profile.getNewWordsPerDay() != null) {
            wanted = profile.getNewWordsPerDay();
            source = SOURCE_CUSTOM;
        } else if (wordsToLearn != null && examDate != null) {
            long daysToLearn = Math.max(1, ChronoUnit.DAYS.between(today, examDate) - REVISION_DAYS_BEFORE_EXAM);
            wanted = (int) Math.ceil(wordsToLearn / (double) daysToLearn);
            source = SOURCE_EXAM;
        } else {
            wanted = srsProperties.getDefaultNewWordsPerDay();
            source = SOURCE_DEFAULT;
        }
        // Chỗ còn lại sau phần ôn nặng hơn trong hai mức: hôm nay (đã ôn + còn đến hạn - không co lại khi người học ôn
        // dần, nên số từ mới không tăng lên giữa ngày), hoặc trung bình tuần tới.
        long reviewLoadToday = reviewedToday + dueReviews;
        int roomForNewWords = (int) (Math.max(0, capacity - Math.max(reviewLoadToday, upcomingPerDay)) / REVIEWS_PER_NEW_WORD);
        // Số người học tự đặt được giữ nguyên: họ đã chọn đánh đổi thời gian.
        int newPerDay = SOURCE_CUSTOM.equals(source) ? wanted : Math.min(wanted, roomForNewWords);
        long newLearnedToday = reviewLogRepository.countNewWordsLearnedSince(userId, calendar.startOfToday());
        long newWaiting = srsRepository.countByUserIdAndLastReviewedAtIsNull(userId);
        int newToday = (int) Math.min(Math.max(0, newPerDay - newLearnedToday), newWaiting);

        double recentNewPerDay = wordsToLearn == null ? 0 : recentNewWordsPerDay(userId, today);
        LocalDate projectedFinish = projectedFinish(wordsToLearn, recentNewPerDay, today);
        Boolean onTrack = examDate == null || wordsToLearn == null || projectedFinish == null
                ? null
                : !projectedFinish.isAfter(examDate.minusDays(REVISION_DAYS_BEFORE_EXAM));

        DailyPlanResponse.NextLesson nextLesson = newWaiting < newPerDay
                ? tagRepository.nextLessonToAdd(userId, levelsUpTo(targetLevel))
                        .map(lesson -> new DailyPlanResponse.NextLesson(lesson.getTagId(), lesson.getName(), lesson.getWords()))
                        .orElse(null)
                : null;

        return DailyPlanResponse.builder()
                .dailyMinutes(dailyMinutes)
                .secondsPerCard(secondsPerCard)
                .reviewCapacity(capacity)
                .dueReviews(dueReviews)
                .reviewsToday(reviewsToday)
                .newPerDay(newPerDay)
                .newPerDayWanted(wanted)
                .newPerDaySource(source)
                .newPerDayLimitedByTime(newPerDay < wanted)
                .newLearnedToday(newLearnedToday)
                .newWaiting(newWaiting)
                .newToday(newToday)
                .estimatedMinutes((int) Math.ceil((reviewsToday + newToday) * secondsPerCard / 60.0))
                .goalSet(profile != null)
                .targetLevel(targetLevel)
                .examDate(examDate)
                .wordsToLearn(wordsToLearn)
                .recentNewPerDay(recentNewPerDay)
                .projectedFinish(projectedFinish)
                .onTrack(onTrack)
                .nextLesson(nextLesson)
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

    /** Các cấp độ từ N5 tới cấp mục tiêu; chưa có mục tiêu thì mọi cấp độ. */
    static List<String> levelsUpTo(JlptLevel targetLevel) {
        return Arrays.stream(JlptLevel.values())
                .filter(level -> targetLevel == null || targetLevel.isAtLeast(level))
                .map(JlptLevel::name)
                .toList();
    }

    /**
     * Số từ mới trung bình mỗi ngày trong {@value #PACE_WINDOW_DAYS} ngày gần đây; người dùng app chưa đủ 2 tuần thì
     * chia cho số ngày đã dùng. Chưa đủ {@value #MIN_PACE_DAYS} ngày thì 0: một hai ngày đầu không nói lên nhịp học.
     */
    private double recentNewWordsPerDay(Long userId, LocalDate today) {
        return reviewLogRepository.firstReviewAt(userId)
                .map(first -> ChronoUnit.DAYS.between(calendar.dayOf(first), today) + 1)
                .filter(daysUsed -> daysUsed >= MIN_PACE_DAYS)
                .map(daysUsed -> {
                    long days = Math.min(PACE_WINDOW_DAYS, daysUsed);
                    LocalDateTime since = calendar.startOf(today.minusDays(days - 1));
                    return reviewLogRepository.countNewWordsLearnedSince(userId, since) / (double) days;
                })
                .orElse(0.0);
    }

    private static LocalDate projectedFinish(Long wordsToLearn, double newPerDay, LocalDate today) {
        if (wordsToLearn == null) {
            return null;
        }
        if (wordsToLearn == 0) {
            return today;
        }
        return newPerDay > 0 ? today.plusDays((long) Math.ceil(wordsToLearn / newPerDay)) : null;
    }
}
