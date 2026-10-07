package com.kanjimastery.backend.model;

/**
 * Thang chấm một lần ôn, giống Anki/FSRS: 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ (cột {@code review_logs.rating}, lưu bằng
 * {@link ReviewRatingConverter}). FSRS dùng thẳng {@link #value()}; SM-2 chấm điểm 0-5 nên dùng {@link #sm2Quality()}.
 */
public enum ReviewRating {
    AGAIN(1, 1),
    HARD(2, 3),
    GOOD(3, 4),
    EASY(4, 5);

    private final int value;
    private final int sm2Quality;

    ReviewRating(int value, int sm2Quality) {
        this.value = value;
        this.sm2Quality = sm2Quality;
    }

    /** Số 1-4 lưu trong DB và dùng trong công thức FSRS. */
    public int value() {
        return value;
    }

    /** Điểm SM-2: Quên 1 (dưới 3 là quên: học lại từ đầu), Khó 3, Nhớ 4, Dễ 5. */
    public int sm2Quality() {
        return sm2Quality;
    }

    /**
     * Mức chấm của số 1-4. Số ngoài khoảng là lỗi lập trình: rating từ người học đã được {@code ReviewRequest} kiểm
     * tra, rating của trắc nghiệm do server tự tính.
     */
    public static ReviewRating fromValue(int value) {
        for (ReviewRating rating : values()) {
            if (rating.value == value) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Mức đánh giá (rating) phải nằm trong khoảng 1-4: " + value);
    }
}
