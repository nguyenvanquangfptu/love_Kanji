package com.kanjimastery.backend.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Tối ưu độ ổn định ban đầu của FSRS (w0-w3: độ ổn định sau lần học đầu, theo mức chấm Quên, Khó, Nhớ, Dễ) theo trí
 * nhớ của từng người. Đây là bước "pretrain" của optimizer chính thức (fsrs-rs), cài lại bằng Java: với mỗi mức chấm,
 * tìm độ ổn định khớp nhất với việc người học còn nhớ hay đã quên từ đó ở lần ôn kế tiếp, sau bao nhiêu ngày.
 * Các tham số còn lại (cách độ ổn định tăng giảm qua từng lần ôn) giữ mặc định: tối ưu chúng cần gradient descent trên
 * toàn bộ lịch sử ôn và rất nhiều dữ liệu hơn.
 * <p>
 * Giống fsrs-rs: tỉ lệ nhớ ở mỗi khoảng cách được làm trơn về tỉ lệ nhớ chung (Laplace), mỗi khoảng cách có trọng số
 * căn bậc hai số mẫu, và độ lệch khỏi giá trị mặc định bị phạt |S - S mặc định| / 16 - ít dữ liệu thì kết quả nằm
 * gần mặc định, càng nhiều bằng chứng càng được lệch xa.
 */
final class FsrsOptimizer {

    /** Trần độ ổn định ban đầu (ngày), như fsrs-rs. */
    static final double MAX_INITIAL_STABILITY = 100;
    private static final double DEFAULT_PENALTY_SCALE = 16;
    private static final int RATINGS = 4;

    /** Lần học đầu của một từ ({@code rating} 1-4) và lần ôn kế tiếp, cách nhau {@code elapsedDays} ngày học. */
    record FirstReview(int rating, long elapsedDays, boolean recalled) {
    }

    private FsrsOptimizer() {
    }

    /** Số lần học đầu dùng được (lần ôn kế tiếp ở một ngày khác) theo mức chấm Quên, Khó, Nhớ, Dễ. */
    static int[] countByRating(List<FirstReview> reviews) {
        int[] counts = new int[RATINGS];
        reviews.stream().filter(FsrsOptimizer::usable).forEach(review -> counts[review.rating() - 1]++);
        return counts;
    }

    /**
     * Độ ổn định ban đầu của 4 mức chấm; rỗng nếu không mức nào có đủ {@code minPerRating} lần học đầu. Mức chưa đủ
     * dữ liệu được suy từ các mức đã tối ưu (giá trị mặc định nhân cùng một tỉ lệ, kẹp giữa hai mức đã tối ưu kề
     * bên), và kết quả luôn tăng dần từ Quên tới Dễ.
     */
    static Optional<double[]> fitInitialStabilities(List<FirstReview> reviews, int minPerRating) {
        List<FirstReview> usable = reviews.stream().filter(FsrsOptimizer::usable).toList();
        int[] counts = countByRating(usable);
        double averageRecall = usable.stream().filter(FirstReview::recalled).count() / (double) Math.max(usable.size(), 1);
        Fsrs fsrs = Fsrs.withDefaults();

        Double[] fitted = new Double[RATINGS];
        for (int rating = 1; rating <= RATINGS; rating++) {
            if (counts[rating - 1] >= minPerRating) {
                int r = rating;
                fitted[rating - 1] = fit(usable.stream().filter(review -> review.rating() == r).toList(),
                        averageRecall, Fsrs.DEFAULT_PARAMETERS[rating - 1], fsrs);
            }
        }
        if (Arrays.stream(fitted).allMatch(value -> value == null)) {
            return Optional.empty();
        }
        keepOrderOfMostSupported(fitted, counts);
        return Optional.of(fillAndOrder(fitted));
    }

    private static boolean usable(FirstReview review) {
        return review.elapsedDays() >= 1 && review.rating() >= 1 && review.rating() <= RATINGS;
    }

    /** Tìm tam phân độ ổn định trong [0,001; 100] cho hàm mất mát (log loss có trọng số + phạt lệch mặc định). */
    private static double fit(List<FirstReview> reviews, double averageRecall, double defaultStability, Fsrs fsrs) {
        // Theo từng khoảng cách: [số lần, số lần còn nhớ].
        Map<Long, long[]> byElapsed = new TreeMap<>();
        for (FirstReview review : reviews) {
            long[] tally = byElapsed.computeIfAbsent(review.elapsedDays(), days -> new long[2]);
            tally[0]++;
            if (review.recalled()) {
                tally[1]++;
            }
        }
        double low = Fsrs.STABILITY_MIN;
        double high = MAX_INITIAL_STABILITY;
        for (int i = 0; i < 1000 && high - low > 1e-6; i++) {
            double third = (high - low) / 3;
            if (loss(byElapsed, averageRecall, low + third, defaultStability, fsrs)
                    < loss(byElapsed, averageRecall, high - third, defaultStability, fsrs)) {
                high -= third;
            } else {
                low += third;
            }
        }
        return (low + high) / 2;
    }

    private static double loss(Map<Long, long[]> byElapsed, double averageRecall, double stability,
                               double defaultStability, Fsrs fsrs) {
        double loss = Math.abs(stability - defaultStability) / DEFAULT_PENALTY_SCALE;
        for (Map.Entry<Long, long[]> entry : byElapsed.entrySet()) {
            long count = entry.getValue()[0];
            double recall = (entry.getValue()[1] + averageRecall) / (count + 1);
            double predicted = Math.min(Math.max(fsrs.retrievability(stability, entry.getKey()), 1e-9), 1 - 1e-9);
            loss -= Math.sqrt(count) * (recall * Math.log(predicted) + (1 - recall) * Math.log(1 - predicted));
        }
        return loss;
    }

    /** Hai mức đã tối ưu mà mức dễ hơn lại ổn định kém hơn: theo mức có nhiều dữ liệu hơn (như fsrs-rs). */
    private static void keepOrderOfMostSupported(Double[] fitted, int[] counts) {
        int[][] pairs = {{0, 1}, {1, 2}, {2, 3}, {0, 2}, {1, 3}, {0, 3}};
        for (int[] pair : pairs) {
            Double easier = fitted[pair[1]];
            Double harder = fitted[pair[0]];
            if (harder != null && easier != null && harder > easier) {
                if (counts[pair[0]] > counts[pair[1]]) {
                    fitted[pair[1]] = harder;
                } else {
                    fitted[pair[0]] = easier;
                }
            }
        }
    }

    private static double[] fillAndOrder(Double[] fitted) {
        double logRatio = 0;
        int known = 0;
        for (int i = 0; i < RATINGS; i++) {
            if (fitted[i] != null) {
                logRatio += Math.log(fitted[i] / Fsrs.DEFAULT_PARAMETERS[i]);
                known++;
            }
        }
        double ratio = Math.exp(logRatio / known);
        double[] stabilities = new double[RATINGS];
        for (int i = 0; i < RATINGS; i++) {
            double value;
            if (fitted[i] != null) {
                value = fitted[i];
            } else {
                value = Fsrs.DEFAULT_PARAMETERS[i] * ratio;
                for (int j = 0; j < RATINGS; j++) {
                    if (fitted[j] != null) {
                        value = j < i ? Math.max(value, fitted[j]) : Math.min(value, fitted[j]);
                    }
                }
            }
            if (i > 0) {
                value = Math.max(value, stabilities[i - 1]);
            }
            stabilities[i] = Math.min(Math.max(value, Fsrs.STABILITY_MIN), MAX_INITIAL_STABILITY);
        }
        return stabilities;
    }
}
