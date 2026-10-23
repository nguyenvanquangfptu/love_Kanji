package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.ProgressResponse;
import com.kanjimastery.backend.dto.StreakResponse;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chuỗi ngày học và lịch nhiệt trên PostgreSQL thật: lần trả lời lúc 3:59 sáng thuộc ngày học hôm trước, 4:01 thuộc
 * hôm nay (ngày học bắt đầu 4 giờ sáng giờ Việt Nam, dù máy chủ chạy múi giờ nào).
 */
class StudyStreakIT extends AbstractIntegrationTest {

    @Autowired
    private ProgressService progressService;
    @Autowired
    private StudyCalendar calendar;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private Long userId;
    private Long wordId;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("streak_" + suffix).email("streak_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
        wordId = kanjiRepository.save(Kanji.builder().character("鬱").hanViet("").meaning("IT").strokeCount(1)
                .jlptLevel(JlptLevel.N3).build()).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteById(wordId);
    }

    @Test
    void streak_shouldSplitDaysAt4amVietnamTime() {
        LocalDate today = calendar.today();
        LocalDateTime startOfToday = calendar.startOf(today);
        answerAt(startOfToday.plusMinutes(1));
        answerAt(startOfToday.plusMinutes(2));
        // 3:59 sáng hôm nay vẫn là ngày học hôm qua.
        answerAt(startOfToday.minusMinutes(1));
        answerAt(calendar.startOf(today.minusDays(3)).plusHours(2));

        StreakResponse streak = progressService.streak(userId);
        assertThat(streak).isEqualTo(new StreakResponse(2, 2, true));

        List<ProgressResponse.CalendarDay> days = progressService.get(userId).getCalendar();
        assertThat(days).hasSize(ProgressService.CALENDAR_DAYS);
        assertThat(days.get(days.size() - 1)).isEqualTo(new ProgressResponse.CalendarDay(today, 2));
        assertThat(days.subList(days.size() - 4, days.size() - 1)).extracting(ProgressResponse.CalendarDay::answers)
                .containsExactly(1L, 0L, 1L);
    }

    private void answerAt(LocalDateTime time) {
        reviewLogRepository.save(ReviewLog.builder().userId(userId).kanjiId(wordId).source(ReviewSource.FLASHCARD)
                .correct(true).rating(ReviewRating.GOOD).stateBefore(CardState.REVIEW).scheduled(true)
                .reviewedAt(time).build());
    }
}
