package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.FsrsState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hoàn tác lần chấm thẻ trên PostgreSQL thật (V36): dòng thẻ trở về y như trước lần chấm - so từng cột - và lần chấm
 * biến mất khỏi lịch sử ôn.
 */
class SrsUndoIT extends AbstractIntegrationTest {

    @Autowired
    private SrsService srsService;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private final List<Long> wordIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("undo_" + suffix).email("undo_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
    }

    @AfterEach
    void tearDown() {
        // Xoá người dùng thì thẻ và lịch sử ôn của họ đi theo (ON DELETE CASCADE).
        userRepository.deleteById(userId);
        kanjiRepository.deleteAllById(wordIds);
    }

    @Test
    void undoReview_shouldLeaveTheCardRowExactlyAsBefore_andForgetTheRating() {
        Long word = wordInReview("鬱");
        Map<String, Object> before = cardRow(word);

        srsService.submitReview(userId, review(word, ReviewRating.AGAIN));
        assertThat(cardRow(word).get("lapse_count")).isEqualTo(3);
        assertThat(answers(word)).isEqualTo(1);

        srsService.undoReview(userId, word);

        assertThat(cardRow(word)).isEqualTo(before);
        assertThat(answers(word)).isZero();
    }

    @Test
    void undoReview_shouldUndoSeveralCards_lastRatedFirst() {
        Long first = wordInReview("鬯");
        Long second = wordInReview("爨");
        Map<String, Object> firstBefore = cardRow(first);
        Map<String, Object> secondBefore = cardRow(second);
        srsService.submitReview(userId, review(first, ReviewRating.GOOD));
        srsService.submitReview(userId, review(second, ReviewRating.HARD));

        srsService.undoReview(userId, second);
        srsService.undoReview(userId, first);

        assertThat(cardRow(first)).isEqualTo(firstBefore);
        assertThat(cardRow(second)).isEqualTo(secondBefore);
        assertThat(answers(first) + answers(second)).isZero();
    }

    @Test
    void undoReview_shouldRefuse_onceTheWordWasAnsweredInAQuizAfterTheRating() {
        Long word = wordInReview("驫");
        srsService.submitReview(userId, review(word, ReviewRating.AGAIN));
        srsService.recordQuizAnswer(userId, word, new SrsService.Answer(ReviewSource.QUIZ,
                QuizDirection.KANJI_TO_READING, false, ReviewRating.AGAIN, 3_000, "うつ"));

        assertThatThrownBy(() -> srsService.undoReview(userId, word)).isInstanceOf(BadRequestException.class);
        assertThat(answers(word)).isEqualTo(2);
    }

    @Test
    void undoReview_calledTwiceAtOnce_shouldUndoExactlyOnce() throws InterruptedException {
        Long word = wordInReview("麤");
        Map<String, Object> before = cardRow(word);
        srsService.submitReview(userId, review(word, ReviewRating.EASY));

        int calls = 2;
        ExecutorService executor = Executors.newFixedThreadPool(calls);
        CountDownLatch ready = new CountDownLatch(calls);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(calls);
        AtomicInteger undone = new AtomicInteger();
        for (int i = 0; i < calls; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    srsService.undoReview(userId, word);
                    undone.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException expected) {
                    // Lần sau bị từ chối: khoá lạc quan của thẻ (409) hoặc không còn lần chấm nào.
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(undone.get()).isEqualTo(1);
        assertThat(cardRow(word)).isEqualTo(before);
        assertThat(answers(word)).isZero();
    }

    /** Từ đã ôn được một thời gian: SM-2 và FSRS đều có trạng thái, quên 2 lần, có ghi chú riêng; đến hạn hôm nay. */
    private Long wordInReview(String character) {
        Long word = kanjiRepository.save(Kanji.builder().character(character).hanViet("").meaning("IT").strokeCount(1)
                .jlptLevel(JlptLevel.N3).build()).getId();
        wordIds.add(word);
        LocalDateTime due = LocalDateTime.now().minusHours(2);
        srsRepository.save(UserKanjiSrs.builder().userId(userId).kanjiId(word)
                .sm2(new Sm2State(3, new BigDecimal("2.36"))).reviewIntervalDays(15)
                .nextReviewAt(due).lastReviewedAt(due.minusDays(15)).lapseCount(2)
                .fsrs(new FsrsState(14.5, 6.25)).personalNote("ghi chú riêng").build());
        return word;
    }

    /** Cả dòng thẻ trừ cột version (khoá lạc quan tăng ở mỗi lần ghi). */
    private Map<String, Object> cardRow(Long word) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM user_kanji_srs WHERE user_id = ? AND kanji_id = ?", userId, word);
        row.remove("version");
        return row;
    }

    private int answers(Long word) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM review_logs WHERE user_id = ? AND kanji_id = ?",
                Integer.class, userId, word);
    }

    private static ReviewRequest review(Long word, ReviewRating rating) {
        ReviewRequest request = new ReviewRequest();
        request.setKanjiId(word);
        request.setRating(rating.value());
        request.setResponseMs(2_000);
        return request;
    }
}
