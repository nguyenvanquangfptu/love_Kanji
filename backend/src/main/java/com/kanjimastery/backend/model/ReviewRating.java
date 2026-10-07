package com.kanjimastery.backend.model;

/**
 * Thang chấm một lần ôn, giống Anki/FSRS: 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ (cột {@code review_logs.rating}).
 * FSRS dùng thẳng thang này; SM-2 chấm điểm 0-5 nên cần đổi qua {@link #toSm2Quality(int)}.
 */
public final class ReviewRating {
    public static final int AGAIN = 1;
    public static final int HARD = 2;
    public static final int GOOD = 3;
    public static final int EASY = 4;

    private ReviewRating() {
    }

    /**
     * Quên 1 (dưới 3 là quên: học lại từ đầu), Khó 3, Nhớ 4, Dễ 5. Mức ngoài 1-4 là lỗi lập trình: rating từ người học đã
     * được {@code ReviewRequest} kiểm tra, rating của trắc nghiệm do server tự tính.
     */
    public static int toSm2Quality(int rating) {
        return switch (rating) {
            case AGAIN -> 1;
            case HARD -> 3;
            case GOOD -> 4;
            case EASY -> 5;
            default -> throw new IllegalArgumentException("Mức đánh giá (rating) phải nằm trong khoảng 1-4: " + rating);
        };
    }
}
