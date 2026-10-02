package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Truy vấn trung vị thời gian trả lời chạy trên PostgreSQL thật (percentile_cont, LIMIT trong subquery). */
class ReviewLogRepositoryIT extends AbstractIntegrationTest {

    private static final String DIRECTION = "KANJI_TO_READING";

    @Autowired
    private ReviewLogRepository reviewLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;

    private Long userId;
    private Long kanjiId;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder()
                .username("review_log_" + suffix)
                .email("review_log_" + suffix + "@test.local")
                .passwordHash("x")
                .build()).getId();
        kanjiId = kanjiRepository.save(Kanji.builder()
                .character("試" + suffix.substring(suffix.length() - 4))
                .hanViet("THÍ")
                .strokeCount(13)
                .jlptLevel("N4")
                .meaning("Thử")
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        // ON DELETE CASCADE dọn luôn review_logs của người dùng/từ thử.
        userRepository.deleteById(userId);
        kanjiRepository.deleteById(kanjiId);
    }

    @Test
    void correctQuizResponseTimes_shouldReturnNullMedianAndZeroSamples_whenNoAnswers() {
        ResponseTimeStats stats = reviewLogRepository.correctQuizResponseTimes(userId, DIRECTION, 200);

        assertThat(stats.getMedianMs()).isNull();
        assertThat(stats.getSamples()).isZero();
    }

    @Test
    void correctQuizResponseTimes_shouldOnlyCountRecentCorrectTimedAnswersOfThatDirection() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        // Cũ nhất, nằm ngoài 3 câu gần nhất -> không tính.
        save(start, ReviewSource.QUIZ, DIRECTION, true, 9_000);
        save(start.plusMinutes(1), ReviewSource.QUIZ, DIRECTION, true, 1_000);
        save(start.plusMinutes(2), ReviewSource.QUIZ, DIRECTION, true, 3_000);
        save(start.plusMinutes(3), ReviewSource.QUIZ, DIRECTION, true, 2_000);
        // Không được tính: trả lời sai, không đo được thời gian, hướng khác, thẻ ôn tập.
        save(start.plusMinutes(4), ReviewSource.QUIZ, DIRECTION, false, 500);
        save(start.plusMinutes(5), ReviewSource.QUIZ, DIRECTION, true, null);
        save(start.plusMinutes(6), ReviewSource.QUIZ, "MEANING", true, 500);
        save(start.plusMinutes(7), ReviewSource.FLASHCARD, null, true, 500);

        ResponseTimeStats stats = reviewLogRepository.correctQuizResponseTimes(userId, DIRECTION, 3);

        assertThat(stats.getSamples()).isEqualTo(3);
        assertThat(stats.getMedianMs()).isEqualTo(2_000.0);
    }

    private void save(LocalDateTime at, String source, String direction, boolean correct, Integer responseMs) {
        reviewLogRepository.save(ReviewLog.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .source(source)
                .direction(direction)
                .correct(correct)
                .rating((short) (correct ? ReviewRating.GOOD : ReviewRating.AGAIN))
                .responseMs(responseMs)
                .stateBefore(CardState.NEW)
                .scheduled(false)
                .reviewedAt(at)
                .build());
    }
}
