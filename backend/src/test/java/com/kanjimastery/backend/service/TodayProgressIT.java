package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.TodayProgressResponse;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hôm nay đã học gì, trên PostgreSQL thật: chỉ tính từ đầu ngày học (4 giờ sáng); từ đã ôn bằng thẻ mỗi từ một lần,
 * từ ôn gần nhất trước; câu trắc nghiệm đếm riêng.
 */
class TodayProgressIT extends AbstractIntegrationTest {

    @Autowired
    private StudyPlanService studyPlanService;
    @Autowired
    private StudyCalendar calendar;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private Long userId;
    private Long first;
    private Long second;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("today_" + suffix).email("today_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
        first = word("鬱");
        second = word("麒");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteAllById(List.of(first, second));
    }

    @Test
    void progressToday_shouldCountOnlyToday_andListTheLatestReviewedWordsFirst() {
        LocalDateTime startOfToday = calendar.startOfToday();
        answer(first, ReviewSource.FLASHCARD, startOfToday.plusMinutes(1));
        answer(second, ReviewSource.FLASHCARD, startOfToday.plusMinutes(2));
        answer(first, ReviewSource.FLASHCARD, startOfToday.plusMinutes(3));
        answer(second, ReviewSource.FLASHCARD, startOfToday.minusMinutes(1));
        answer(second, ReviewSource.QUIZ, startOfToday.plusMinutes(4));
        answer(second, ReviewSource.QUIZ, startOfToday.minusHours(1));

        TodayProgressResponse today = studyPlanService.progressToday(userId);

        assertThat(today.cardsAnswered()).isEqualTo(3);
        assertThat(today.recentWords()).containsExactly(first, second);
        assertThat(today.quizAnswers()).isEqualTo(1);
    }

    private Long word(String character) {
        return kanjiRepository.save(Kanji.builder().character(character).hanViet("").meaning("IT").strokeCount(1)
                .jlptLevel(JlptLevel.N3).build()).getId();
    }

    private void answer(Long wordId, ReviewSource source, LocalDateTime time) {
        reviewLogRepository.save(ReviewLog.builder().userId(userId).kanjiId(wordId).source(source)
                .direction(source == ReviewSource.QUIZ ? QuizDirection.MEANING : null)
                .correct(true).rating(ReviewRating.GOOD).stateBefore(CardState.REVIEW).scheduled(true)
                .reviewedAt(time).build());
    }
}
