package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.ReviewLogRepository.DirectionStats;
import com.kanjimastery.backend.repository.ReviewLogRepository.FirstReviewOutcome;
import com.kanjimastery.backend.repository.ReviewLogRepository.QuizMistake;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import com.kanjimastery.backend.repository.ReviewLogRepository.WordDirectionStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Các truy vấn thống kê review_logs chạy trên PostgreSQL thật (percentile_cont, FILTER, mốc thời gian). */
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
    private final List<Long> otherKanjiIds = new ArrayList<>();

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
        kanjiRepository.deleteAllById(otherKanjiIds);
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

    @Test
    void wordDirectionStats_shouldCountAnswersMistakesAndRecentMistakesPerDirection() {
        LocalDateTime now = LocalDateTime.now();
        save(now.minusDays(20), ReviewSource.QUIZ, DIRECTION, false, 1_000);
        save(now.minusDays(1), ReviewSource.QUIZ, DIRECTION, true, 1_000);
        save(now.minusDays(1), ReviewSource.QUIZ, "READING_TO_KANJI", false, 1_000);
        save(now.minusHours(1), ReviewSource.FLASHCARD, null, false, null);

        Map<String, WordDirectionStats> byDirection = reviewLogRepository
                .wordDirectionStats(userId, List.of(kanjiId), now.minusDays(14)).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.getDirection()), Function.identity()));

        assertThat(byDirection).containsOnlyKeys(DIRECTION, "READING_TO_KANJI", "null");
        assertThat(byDirection.get(DIRECTION)).extracting(
                WordDirectionStats::getAnswers, WordDirectionStats::getErrors, WordDirectionStats::getRecentErrors)
                .containsExactly(2L, 1L, 0L);
        assertThat(byDirection.get("READING_TO_KANJI").getRecentErrors()).isEqualTo(1);
        assertThat(byDirection.get("null").getRecentErrors()).isEqualTo(1);
        assertThat(byDirection.values()).allSatisfy(row -> assertThat(row.getKanjiId()).isEqualTo(kanjiId));
    }

    @Test
    void quizDirectionStats_shouldOnlyCountQuizAnswersSinceTheGivenTime() {
        LocalDateTime now = LocalDateTime.now();
        save(now.minusDays(40), ReviewSource.QUIZ, DIRECTION, false, 1_000);
        save(now.minusDays(2), ReviewSource.QUIZ, DIRECTION, false, 1_000);
        save(now.minusDays(2), ReviewSource.QUIZ, DIRECTION, true, 1_000);
        save(now.minusDays(2), ReviewSource.FLASHCARD, null, false, null);

        List<DirectionStats> rows = reviewLogRepository.quizDirectionStats(userId, now.minusDays(30));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getDirection()).isEqualTo(DIRECTION);
            assertThat(row.getAnswers()).isEqualTo(2);
            assertThat(row.getErrors()).isEqualTo(1);
        });
    }

    @Test
    void quizMistakes_shouldGroupWrongQuizChoices_mostFrequentFirst() {
        LocalDateTime now = LocalDateTime.now();
        saveAnswer(now.minusDays(5), DIRECTION, false, "しょるい");
        saveAnswer(now.minusDays(3), DIRECTION, false, "しゅへん");
        saveAnswer(now.minusDays(2), DIRECTION, false, "しゅへん");
        saveAnswer(now.minusDays(1), "READING_TO_KANJI", false, "周辺");
        // Không tính: trả lời đúng.
        saveAnswer(now.minusHours(1), DIRECTION, true, "しゅうへん");

        List<QuizMistake> mistakes = reviewLogRepository.quizMistakes(userId, List.of(kanjiId));

        assertThat(mistakes).extracting(QuizMistake::getDirection, QuizMistake::getChosenAnswer, QuizMistake::getTimes)
                .containsExactly(
                        tuple(DIRECTION, "しゅへん", 2L),
                        tuple("READING_TO_KANJI", "周辺", 1L),
                        tuple(DIRECTION, "しょるい", 1L));
    }

    @Test
    void flashcardPace_shouldTakeTheMedianGapBetweenFlashcardReviews_ignoringBreaks() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        save(start, ReviewSource.FLASHCARD, null, true, null);
        save(start.plusSeconds(5), ReviewSource.FLASHCARD, null, true, null);
        save(start.plusSeconds(6), ReviewSource.QUIZ, DIRECTION, true, 1_000);   // không phải thẻ ôn
        save(start.plusSeconds(12), ReviewSource.FLASHCARD, null, true, null);
        save(start.plusMinutes(10), ReviewSource.FLASHCARD, null, true, null);    // nghỉ giữa chừng: bỏ
        save(start.plusMinutes(10).plusSeconds(8), ReviewSource.FLASHCARD, null, true, null);

        ResponseTimeStats pace = reviewLogRepository.flashcardPace(userId, 300);

        // Các khoảng 5 s, 7 s, 8 s.
        assertThat(pace.getSamples()).isEqualTo(3);
        assertThat(pace.getMedianMs()).isEqualTo(7_000.0);
    }

    @Test
    void countNewWordsLearnedSince_shouldCountFirstScheduledReviewsOnly() {
        LocalDateTime since = LocalDateTime.now().minusHours(2);
        saveReview(since.plusMinutes(5), CardState.NEW, true);
        saveReview(since.plusMinutes(6), CardState.NEW, false);       // chỉ ghi lại, không vào lịch ôn
        saveReview(since.plusMinutes(7), CardState.REVIEW, true);     // không phải lần học đầu
        saveReview(since.minusMinutes(1), CardState.NEW, true);       // trước mốc

        assertThat(reviewLogRepository.countNewWordsLearnedSince(userId, since)).isEqualTo(1);
        assertThat(reviewLogRepository.countNewWordsLearnedSince(userId, since.plusMinutes(10))).isZero();
    }

    @Test
    void topQuizMistakes_shouldKeepTheMostFrequentRecentOnes_upToTheLimit() {
        LocalDateTime now = LocalDateTime.now();
        saveAnswer(now.minusDays(100), DIRECTION, false, "cũ quá");
        saveAnswer(now.minusDays(100), DIRECTION, false, "cũ quá");
        saveAnswer(now.minusDays(100), DIRECTION, false, "cũ quá");
        saveAnswer(now.minusDays(3), DIRECTION, false, "しゅへん");
        saveAnswer(now.minusDays(2), DIRECTION, false, "しゅへん");
        saveAnswer(now.minusDays(1), DIRECTION, false, "しょるい");
        saveAnswer(now.minusHours(1), "MEANING", false, "Xung quanh");

        List<QuizMistake> mistakes = reviewLogRepository.topQuizMistakes(userId, now.minusDays(90), 2);

        assertThat(mistakes).extracting(QuizMistake::getChosenAnswer, QuizMistake::getTimes)
                .containsExactly(tuple("しゅへん", 2L), tuple("Xung quanh", 1L));
    }

    @Test
    void activitySince_shouldReturnEveryAnswerSinceTheGivenTime() {
        LocalDateTime now = LocalDateTime.now();
        saveReview(now.minusDays(2), CardState.REVIEW, true);
        saveReview(now.minusHours(1), CardState.NEW, false);
        saveReview(now.minusDays(20), CardState.REVIEW, true);

        List<ReviewLogRepository.Activity> activity = reviewLogRepository.activitySince(userId, now.minusDays(7));

        assertThat(activity).hasSize(2);
        assertThat(activity).extracting(ReviewLogRepository.Activity::getStateBefore,
                        ReviewLogRepository.Activity::getScheduled, ReviewLogRepository.Activity::getCorrect)
                .containsExactlyInAnyOrder(tuple(CardState.REVIEW, true, true), tuple(CardState.NEW, false, true));
    }

    @Test
    void firstReviewOutcomes_shouldPairEachWordsFirstReviewWithItsNextScheduledReview() {
        LocalDateTime learned = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusDays(10);
        // Học "Khó", 2 ngày sau ôn thì quên; câu trắc nghiệm đúng ở giữa chỉ được ghi lại, không tính.
        saveReview(kanjiId, learned, CardState.NEW, ReviewRating.HARD, true);
        saveReview(kanjiId, learned.plusDays(1), CardState.REVIEW, ReviewRating.GOOD, false);
        saveReview(kanjiId, learned.plusDays(2), CardState.REVIEW, ReviewRating.AGAIN, true);
        saveReview(kanjiId, learned.plusDays(3), CardState.RELEARNING, ReviewRating.GOOD, true);
        // Mới học, chưa ôn lại lần nào.
        Long notReviewedYet = otherKanji("未");
        saveReview(notReviewedYet, learned, CardState.NEW, ReviewRating.GOOD, true);
        // Đã ôn từ trước khi có log: không biết lần học đầu ra sao.
        Long learnedBeforeLogs = otherKanji("既");
        saveReview(learnedBeforeLogs, learned, CardState.REVIEW, ReviewRating.GOOD, true);
        saveReview(learnedBeforeLogs, learned.plusDays(4), CardState.REVIEW, ReviewRating.GOOD, true);

        assertThat(reviewLogRepository.firstReviewOutcomes(userId))
                .extracting(FirstReviewOutcome::getRating, FirstReviewOutcome::getFirstAt, FirstReviewOutcome::getNextAt,
                        FirstReviewOutcome::getRecalled)
                .containsExactly(tuple((short) ReviewRating.HARD, learned, learned.plusDays(2), false));
    }

    @Test
    void userIdsActiveSince_shouldListLearnersWhoAnsweredSinceTheGivenTime() {
        LocalDateTime now = LocalDateTime.now();
        saveReview(now.minusDays(2), CardState.NEW, true);

        assertThat(reviewLogRepository.userIdsActiveSince(now.minusDays(7))).contains(userId);
        assertThat(reviewLogRepository.userIdsActiveSince(now.minusDays(1))).doesNotContain(userId);
    }

    private Long otherKanji(String character) {
        String suffix = String.valueOf(System.nanoTime());
        Long id = kanjiRepository.save(Kanji.builder()
                .character(character + suffix.substring(suffix.length() - 4))
                .hanViet("THÍ")
                .strokeCount(5)
                .jlptLevel("N4")
                .meaning("Thử")
                .build()).getId();
        otherKanjiIds.add(id);
        return id;
    }

    private void saveReview(Long kanji, LocalDateTime at, String stateBefore, int rating, boolean scheduled) {
        reviewLogRepository.save(ReviewLog.builder()
                .userId(userId)
                .kanjiId(kanji)
                .source(ReviewSource.FLASHCARD)
                .correct(rating != ReviewRating.AGAIN)
                .rating((short) rating)
                .stateBefore(stateBefore)
                .scheduled(scheduled)
                .reviewedAt(at)
                .build());
    }

    private void saveReview(LocalDateTime at, String stateBefore, boolean scheduled) {
        reviewLogRepository.save(ReviewLog.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .source(ReviewSource.FLASHCARD)
                .correct(true)
                .rating((short) ReviewRating.GOOD)
                .stateBefore(stateBefore)
                .scheduled(scheduled)
                .reviewedAt(at)
                .build());
    }

    private void saveAnswer(LocalDateTime at, String direction, boolean correct, String chosenAnswer) {
        save(at, ReviewSource.QUIZ, direction, correct, 1_000, chosenAnswer);
    }

    private void save(LocalDateTime at, String source, String direction, boolean correct, Integer responseMs) {
        save(at, source, direction, correct, responseMs, null);
    }

    private void save(LocalDateTime at, String source, String direction, boolean correct, Integer responseMs,
                      String chosenAnswer) {
        reviewLogRepository.save(ReviewLog.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .source(source)
                .direction(direction)
                .correct(correct)
                .rating((short) (correct ? ReviewRating.GOOD : ReviewRating.AGAIN))
                .responseMs(responseMs)
                .chosenAnswer(chosenAnswer)
                .stateBefore(CardState.NEW)
                .scheduled(false)
                .reviewedAt(at)
                .build());
    }
}
