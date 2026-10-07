package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.service.AdaptiveQuizPlanner.Group;
import com.kanjimastery.backend.service.LearnerHistory.Tally;
import com.kanjimastery.backend.service.LearnerHistory.WordHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveQuizPlannerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);

    @Test
    void group_shouldClassifyWordsByCardStateAndRecentMistakes() {
        Map<Long, UserKanjiSrs> cards = Map.of(
                1L, card(1L, NOW.minusHours(1), 6, "2.50"),   // đến hạn
                2L, card(2L, NOW.plusDays(3), 6, "1.80"),     // EF thấp
                3L, card(3L, NOW.plusDays(10), 30, "2.60"),   // đã thuộc
                4L, card(4L, NOW.plusDays(2), 6, "2.50"));    // đang học
        Map<Long, WordHistory> words = Map.of(
                5L, new WordHistory(Map.of(), 1),                                   // vừa sai, chưa có thẻ
                6L, new WordHistory(Map.of(KANJI_TO_READING, new Tally(2, 0)), 0)); // trả lời đúng, không có thẻ
        LearnerHistory history = new LearnerHistory(NOW, cards, words, Map.of());

        assertThat(LongStream.rangeClosed(1, 7).mapToObj(id -> AdaptiveQuizPlanner.group(word(id), history)))
                .containsExactly(Group.WEAK, Group.WEAK, Group.CHECK, Group.OTHER, Group.WEAK, Group.OTHER, Group.NEW);
    }

    @Test
    void selectWords_shouldMixWeakNewAndMasteredWords() {
        Map<Long, UserKanjiSrs> cards = new HashMap<>();
        for (long id = 1; id <= 20; id++) {
            cards.put(id, card(id, NOW.minusDays(1), 6, "2.50"));
        }
        for (long id = 41; id <= 60; id++) {
            cards.put(id, card(id, NOW.plusDays(20), 40, "2.70"));
        }
        LearnerHistory history = new LearnerHistory(NOW, cards, Map.of(), Map.of());
        List<Kanji> pool = words(1, 60); // 1-20 yếu, 21-40 chưa gặp, 41-60 đã thuộc

        List<Kanji> selected = AdaptiveQuizPlanner.selectWords(pool, 20, history, new Random(1));

        assertThat(selected).hasSize(20).doesNotHaveDuplicates();
        assertThat(countByGroup(selected, history))
                .containsEntry(Group.WEAK, 12L)
                .containsEntry(Group.NEW, 5L)
                .containsEntry(Group.CHECK, 3L);
    }

    @Test
    void selectWords_shouldFillGroupsThatRunShort_withTheRemainingWords() {
        Map<Long, UserKanjiSrs> cards = new HashMap<>();
        cards.put(1L, card(1L, NOW.minusDays(2), 6, "2.50"));
        cards.put(2L, card(2L, NOW.minusDays(2), 6, "2.50"));
        for (long id = 3; id <= 12; id++) {
            cards.put(id, card(id, NOW.plusDays(2), 6, "2.50"));
        }
        LearnerHistory history = new LearnerHistory(NOW, cards, Map.of(), Map.of());

        List<Kanji> selected = AdaptiveQuizPlanner.selectWords(words(1, 12), 10, history, new Random(3));

        assertThat(selected).hasSize(10).doesNotHaveDuplicates();
        assertThat(selected).extracting(Kanji::getId).contains(1L, 2L);
    }

    @Test
    void selectWords_shouldTakeEveryWord_whenThePoolIsSmallerThanTheQuiz() {
        List<Kanji> pool = words(1, 4);

        List<Kanji> selected = AdaptiveQuizPlanner.selectWords(pool, 10, LearnerHistory.empty(NOW), new Random(5));

        assertThat(selected).containsExactlyInAnyOrderElementsOf(pool);
    }

    @Test
    void selectWords_shouldPickWordsMissedMoreOften_moreOften() {
        // Từ 1 sai 5 lần gần đây (trọng số 11), từ 2 chỉ vừa đến hạn (trọng số ~1): 11/12 lượt nên chọn từ 1.
        LearnerHistory history = new LearnerHistory(NOW,
                Map.of(2L, card(2L, NOW.minusHours(1), 6, "2.50")),
                Map.of(1L, new WordHistory(Map.of(), 5)),
                Map.of());
        List<Kanji> pool = words(1, 2);
        Random random = new Random(7);

        long picksOfMissedWord = LongStream.range(0, 2000)
                .filter(i -> AdaptiveQuizPlanner.selectWords(pool, 1, history, random).get(0).getId() == 1L)
                .count();

        assertThat(picksOfMissedWord).isBetween(1700L, 1950L);
    }

    @Test
    void chooseDirection_shouldBeEven_withoutAnyHistory() {
        assertThat(readingQuestionsOutOf2000(word(1L), LearnerHistory.empty(NOW))).isBetween(900L, 1100L);
    }

    @Test
    void chooseDirection_shouldLeanTowardsTheDirectionTheLearnerGetsWrongForThatWord() {
        // Hỏi cách đọc 4 lần đúng cả 4, hỏi cách viết 3 lần sai 2: nên hỏi cách viết ~72% số lần.
        LearnerHistory history = new LearnerHistory(NOW, Map.of(), Map.of(1L, new WordHistory(Map.of(
                KANJI_TO_READING, new Tally(4, 0),
                READING_TO_KANJI, new Tally(3, 2)), 2)), Map.of());

        assertThat(readingQuestionsOutOf2000(word(1L), history)).isBetween(450L, 650L);
    }

    @Test
    void chooseDirection_shouldFollowTheLearnersOverallWeakness_forAnUnseenWord() {
        // Người học hay sai khi chọn cách viết (10/20) nhưng hầu như không sai cách đọc (0/20): ~80% hỏi cách viết.
        LearnerHistory history = new LearnerHistory(NOW, Map.of(), Map.of(), Map.of(
                KANJI_TO_READING, new Tally(20, 0),
                READING_TO_KANJI, new Tally(20, 10)));

        assertThat(readingQuestionsOutOf2000(word(1L), history)).isBetween(280L, 500L);
    }

    @Test
    void sample_shouldReturnDistinctItemsUpToK() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        assertThat(AdaptiveQuizPlanner.sample(items, 3, item -> item, new Random(9))).hasSize(3).doesNotHaveDuplicates();
        assertThat(AdaptiveQuizPlanner.sample(items, 9, item -> item, new Random(9))).hasSize(5);
        assertThat(AdaptiveQuizPlanner.sample(items, 0, item -> item, new Random(9))).isEmpty();
    }

    private static long readingQuestionsOutOf2000(Kanji kanji, LearnerHistory history) {
        Random random = new Random(11);
        return LongStream.range(0, 2000)
                .filter(i -> AdaptiveQuizPlanner.chooseDirection(kanji, history, random).equals(KANJI_TO_READING))
                .count();
    }

    private static Map<Group, Long> countByGroup(List<Kanji> selected, LearnerHistory history) {
        return selected.stream().collect(Collectors.groupingBy(
                kanji -> AdaptiveQuizPlanner.group(kanji, history), Collectors.counting()));
    }

    private static List<Kanji> words(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId).mapToObj(AdaptiveQuizPlannerTest::word).toList();
    }

    private static Kanji word(long id) {
        return Kanji.builder().id(id).character("語" + id).reading("ご" + id).meaning("nghĩa " + id).build();
    }

    private static UserKanjiSrs card(long kanjiId, LocalDateTime nextReviewAt, int intervalDays, String easiness) {
        return UserKanjiSrs.builder()
                .kanjiId(kanjiId)
                .sm2(new Sm2State(3, new BigDecimal(easiness)))
                
                .reviewIntervalDays(intervalDays)
                .nextReviewAt(nextReviewAt)
                .lastReviewedAt(nextReviewAt.minusDays(intervalDays))
                .build();
    }
}
