package com.kanjimastery.backend.service;

import java.util.Arrays;

/**
 * Mô hình trí nhớ FSRS-6 (Free Spaced Repetition Scheduler, open-spaced-repetition), cài lại theo đúng công thức của
 * thư viện tham chiếu py-fsrs: độ ổn định S (số ngày để xác suất nhớ còn 90%), độ khó D (1-10) và xác suất nhớ R.
 * Không có bước học tính bằng phút như Anki - app lập lịch theo ngày, nên mọi thẻ đi thẳng vào chế độ ôn.
 * Được kiểm chứng bằng chính các test của py-fsrs (FsrsTest).
 */
public final class Fsrs {

    /** 21 tham số mặc định của FSRS-6, huấn luyện trên dữ liệu ôn của rất nhiều người dùng. */
    public static final double[] DEFAULT_PARAMETERS = {
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542};
    static final double STABILITY_MIN = 0.001;
    static final double MIN_DIFFICULTY = 1;
    static final double MAX_DIFFICULTY = 10;
    static final int MAXIMUM_INTERVAL = 36_500;

    /** Trạng thái trí nhớ của một thẻ. */
    public record Memory(double stability, double difficulty) {
    }

    private final double[] w;
    private final double decay;
    private final double factor;

    public Fsrs(double[] parameters) {
        if (parameters.length != DEFAULT_PARAMETERS.length) {
            throw new IllegalArgumentException("FSRS-6 cần đúng 21 tham số, nhận " + parameters.length);
        }
        this.w = Arrays.copyOf(parameters, parameters.length);
        this.decay = -w[20];
        this.factor = Math.pow(0.9, 1 / decay) - 1;
    }

    public static Fsrs withDefaults() {
        return new Fsrs(DEFAULT_PARAMETERS);
    }

    /** Lần học đầu tiên của một thẻ. {@code rating}: 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ. */
    public Memory first(int rating) {
        return new Memory(initialStability(rating), clampDifficulty(initialDifficulty(rating)));
    }

    /**
     * Một lần ôn sau lần ôn trước {@code elapsedDays} ngày. Cùng ngày (0 ngày) dùng công thức ngắn hạn; còn lại tính
     * theo xác suất nhớ lúc ôn: nhớ thì độ ổn định tăng (càng sắp quên mà vẫn nhớ thì tăng càng nhiều), quên thì giảm.
     * Độ ổn định mới tính bằng độ khó cũ, rồi mới cập nhật độ khó.
     */
    public Memory next(Memory memory, int rating, long elapsedDays) {
        double stability;
        if (elapsedDays < 1) {
            stability = shortTermStability(memory.stability(), rating);
        } else {
            double recall = retrievability(memory.stability(), elapsedDays);
            stability = clampStability(rating == 1
                    ? forgetStability(memory.difficulty(), memory.stability(), recall)
                    : recallStability(memory.difficulty(), memory.stability(), recall, rating));
        }
        return new Memory(stability, nextDifficulty(memory.difficulty(), rating));
    }

    /** Xác suất còn nhớ sau {@code elapsedDays} ngày với độ ổn định {@code stability}. */
    public double retrievability(double stability, double elapsedDays) {
        return Math.pow(1 + factor * Math.max(elapsedDays, 0) / stability, decay);
    }

    /** Số ngày tới lần ôn sau để xác suất nhớ lúc đó bằng {@code desiredRetention} (1 - 36500 ngày). */
    public int interval(double stability, double desiredRetention) {
        long days = Math.round(stability / factor * (Math.pow(desiredRetention, 1 / decay) - 1));
        return (int) Math.min(Math.max(days, 1), MAXIMUM_INTERVAL);
    }

    private double initialStability(int rating) {
        return clampStability(w[rating - 1]);
    }

    private double initialDifficulty(int rating) {
        return w[4] - Math.exp(w[5] * (rating - 1)) + 1;
    }

    private double shortTermStability(double stability, int rating) {
        double increase = Math.exp(w[17] * (rating - 3 + w[18])) * Math.pow(stability, -w[19]);
        if (rating > 1) {
            increase = Math.max(increase, 1);
        }
        return clampStability(stability * increase);
    }

    /** Độ khó dịch theo mức đánh giá (giảm dần khi gần 10), rồi kéo nhẹ về độ khó của một thẻ "Dễ". */
    private double nextDifficulty(double difficulty, int rating) {
        double delta = -(w[6] * (rating - 3));
        double damped = difficulty + (10 - difficulty) * delta / 9;
        double reverted = w[7] * initialDifficulty(4) + (1 - w[7]) * damped;
        return clampDifficulty(reverted);
    }

    private double forgetStability(double difficulty, double stability, double recall) {
        double longTerm = w[11] * Math.pow(difficulty, -w[12]) * (Math.pow(stability + 1, w[13]) - 1)
                * Math.exp((1 - recall) * w[14]);
        double shortTerm = stability / Math.exp(w[17] * w[18]);
        return Math.min(longTerm, shortTerm);
    }

    private double recallStability(double difficulty, double stability, double recall, int rating) {
        double hardPenalty = rating == 2 ? w[15] : 1;
        double easyBonus = rating == 4 ? w[16] : 1;
        return stability * (1 + Math.exp(w[8]) * (11 - difficulty) * Math.pow(stability, -w[9])
                * (Math.exp((1 - recall) * w[10]) - 1) * hardPenalty * easyBonus);
    }

    private static double clampStability(double stability) {
        return Math.max(stability, STABILITY_MIN);
    }

    private static double clampDifficulty(double difficulty) {
        return Math.min(Math.max(difficulty, MIN_DIFFICULTY), MAX_DIFFICULTY);
    }
}
