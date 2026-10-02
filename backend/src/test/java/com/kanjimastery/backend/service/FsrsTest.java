package com.kanjimastery.backend.service;

import com.kanjimastery.backend.service.Fsrs.Memory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden test: cùng chuỗi ôn với các test của thư viện tham chiếu py-fsrs (tests/test_basic.py), kết quả phải khớp.
 * py-fsrs có bước học 1 và 10 phút; ở đây các bước đó là những lần ôn cách 0 ngày.
 */
class FsrsTest {

    private static final int AGAIN = 1;
    private static final int HARD = 2;
    private static final int GOOD = 3;
    private static final int EASY = 4;

    private final Fsrs fsrs = Fsrs.withDefaults();

    @Test
    void memoryState_shouldMatchPyFsrs_testMemoState() {
        Memory memory = fsrs.first(AGAIN);
        long[] elapsed = {0, 1, 3, 8, 21};
        for (long days : elapsed) {
            memory = fsrs.next(memory, GOOD, days);
        }

        assertThat(memory.stability()).isCloseTo(53.62691, within(1e-4));
        assertThat(memory.difficulty()).isCloseTo(6.3574867, within(1e-4));
    }

    @Test
    void intervals_shouldMatchPyFsrs_testReviewCard() {
        // TEST_RATINGS_1 của py-fsrs: Nhớ x6, Quên x2, Nhớ x5. Khoảng ôn 0 là bước học/học lại 10 phút.
        Memory memory = fsrs.first(GOOD);
        List<Integer> intervals = new ArrayList<>(List.of(0));
        int[][] reviews = {
                {GOOD, 0}, {GOOD, 2}, {GOOD, 11}, {GOOD, 46}, {GOOD, 163},
                {AGAIN, 498}, {AGAIN, 0}, {GOOD, 0}, {GOOD, 2}, {GOOD, 4}, {GOOD, 7}, {GOOD, 12}};
        for (int i = 0; i < reviews.length; i++) {
            memory = fsrs.next(memory, reviews[i][0], reviews[i][1]);
            boolean stillRelearning = i == 5 || i == 6;
            intervals.add(stillRelearning ? 0 : fsrs.interval(memory.stability(), 0.9));
        }

        assertThat(intervals).containsExactly(0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21);
    }

    @Test
    void difficulty_shouldBottomOutAtOne_afterRepeatedEasyReviews() {
        Memory memory = fsrs.first(EASY);
        for (int i = 0; i < 9; i++) {
            memory = fsrs.next(memory, EASY, 0);
        }

        assertThat(memory.difficulty()).isEqualTo(1.0);
    }

    @Test
    void sameDayHardReview_shouldNotDecreaseStability() {
        Memory memory = fsrs.first(GOOD);

        assertThat(fsrs.next(memory, HARD, 0).stability()).isEqualTo(memory.stability());
    }

    @Test
    void stability_shouldNeverDropBelowTheMinimum() {
        Memory memory = fsrs.first(AGAIN);
        for (int i = 0; i < 1000; i++) {
            memory = fsrs.next(memory, AGAIN, fsrs.interval(memory.stability(), 0.9) + 1);
            assertThat(memory.stability()).isGreaterThanOrEqualTo(Fsrs.STABILITY_MIN);
        }
    }

    @Test
    void interval_shouldReachTheDesiredRetention_andGrowWhenAskingForLess() {
        double stability = 20;

        // Độ ổn định S nghĩa là sau S ngày xác suất nhớ còn 90%.
        assertThat(fsrs.retrievability(stability, 20)).isCloseTo(0.9, within(1e-9));
        assertThat(fsrs.interval(stability, 0.9)).isEqualTo(20);
        assertThat(fsrs.interval(stability, 0.8)).isGreaterThan(20);
        assertThat(fsrs.interval(stability, 0.95)).isLessThan(20);
    }

    @Test
    void constructor_shouldRefuseAParameterSetOfTheWrongSize() {
        assertThatThrownBy(() -> new Fsrs(new double[19])).isInstanceOf(IllegalArgumentException.class);
    }
}
