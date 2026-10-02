package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.service.FsrsOptimizer.FirstReview;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FsrsOptimizerTest {

    private static final double DEFAULT_GOOD = Fsrs.DEFAULT_PARAMETERS[ReviewRating.GOOD - 1];

    @Test
    void countByRating_shouldOnlyCountNextReviewsOnAnotherDay() {
        List<FirstReview> reviews = List.of(
                new FirstReview(ReviewRating.GOOD, 1, true),
                new FirstReview(ReviewRating.GOOD, 0, true),
                new FirstReview(ReviewRating.AGAIN, 3, false),
                new FirstReview(ReviewRating.EASY, 9, true));

        assertThat(FsrsOptimizer.countByRating(reviews)).containsExactly(1, 0, 1, 1);
    }

    @Test
    void fit_shouldKeepTheDefaults_untilARatingHasEnoughFirstReviews() {
        assertThat(FsrsOptimizer.fitInitialStabilities(reviews(ReviewRating.GOOD, 1, 49, 40), 50)).isEmpty();
        // Ôn lại ngay trong ngày không cho biết gì về trí nhớ dài hạn.
        assertThat(FsrsOptimizer.fitInitialStabilities(reviews(ReviewRating.GOOD, 0, 80, 70), 50)).isEmpty();
    }

    @Test
    void fit_shouldShortenTheFirstInterval_forALearnerWhoForgetsMoreThanUsual() {
        // FSRS chung đoán 95% còn nhớ một từ "Nhớ" sau 1 ngày; người này chỉ nhớ 60%.
        double[] stabilities = FsrsOptimizer.fitInitialStabilities(reviews(ReviewRating.GOOD, 1, 100, 60), 50)
                .orElseThrow();

        assertThat(stabilities[ReviewRating.GOOD - 1]).isLessThan(0.5);
        // Các mức chưa có dữ liệu được kéo theo cùng tỉ lệ.
        assertThat(stabilities[ReviewRating.EASY - 1]).isLessThan(Fsrs.DEFAULT_PARAMETERS[ReviewRating.EASY - 1]);
        assertThat(stabilities).isSorted();
    }

    @Test
    void fit_shouldLengthenTheFirstInterval_forALearnerWhoRemembersBetterThanUsual() {
        // Sau 4 ngày FSRS chung đoán còn nhớ ~86%; người này nhớ 99 trên 100 từ.
        double[] stabilities = FsrsOptimizer.fitInitialStabilities(reviews(ReviewRating.GOOD, 4, 100, 99), 50)
                .orElseThrow();

        assertThat(stabilities[ReviewRating.GOOD - 1]).isGreaterThan(2 * DEFAULT_GOOD);
        assertThat(stabilities).isSorted();
    }

    @Test
    void fit_shouldStayNearTheDefault_whenTheEvidenceIsThin() {
        double few = FsrsOptimizer.fitInitialStabilities(reviews(ReviewRating.GOOD, 4, 50, 50), 50)
                .orElseThrow()[ReviewRating.GOOD - 1];
        double many = FsrsOptimizer.fitInitialStabilities(reviews(ReviewRating.GOOD, 4, 1000, 1000), 50)
                .orElseThrow()[ReviewRating.GOOD - 1];

        assertThat(few).isGreaterThan(DEFAULT_GOOD).isLessThan(many);
        assertThat(many).isLessThanOrEqualTo(FsrsOptimizer.MAX_INITIAL_STABILITY);
    }

    @Test
    void fit_shouldNeverMakeAnEasierFirstRatingLessStable() {
        // "Quên" mà vẫn nhớ tốt hơn "Nhớ": mức có nhiều dữ liệu hơn (Quên) được giữ, mức kia nâng lên theo.
        List<FirstReview> reviews = new ArrayList<>(reviews(ReviewRating.AGAIN, 2, 200, 190));
        reviews.addAll(reviews(ReviewRating.GOOD, 2, 60, 30));

        double[] stabilities = FsrsOptimizer.fitInitialStabilities(reviews, 50).orElseThrow();

        assertThat(stabilities).isSorted();
        assertThat(stabilities[ReviewRating.GOOD - 1]).isEqualTo(stabilities[ReviewRating.AGAIN - 1]);
        // Mức Khó chưa có dữ liệu nằm giữa hai mức đã tối ưu.
        assertThat(stabilities[ReviewRating.HARD - 1]).isEqualTo(stabilities[ReviewRating.AGAIN - 1]);
    }

    /** {@code count} từ học lần đầu với {@code rating}, ôn lại sau {@code days} ngày, {@code recalled} từ còn nhớ. */
    private static List<FirstReview> reviews(int rating, long days, int count, int recalled) {
        return IntStream.range(0, count).mapToObj(i -> new FirstReview(rating, days, i < recalled)).toList();
    }
}
