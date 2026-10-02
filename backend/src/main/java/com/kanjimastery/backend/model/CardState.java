package com.kanjimastery.backend.model;

/** Trạng thái thẻ ôn ngay trước một lần trả lời (cột {@code review_logs.state_before}). */
public final class CardState {
    /** Chưa ôn lần nào (chưa có trong lịch ôn, hoặc vừa được thêm mà chưa lật). */
    public static final String NEW = "NEW";
    /** Đang ôn bình thường. */
    public static final String REVIEW = "REVIEW";
    /** Vừa quên (SM-2 đã đưa số lần nhớ liên tiếp về 0) và đang học lại. */
    public static final String RELEARNING = "RELEARNING";

    private CardState() {
    }

    public static String of(UserKanjiSrs card) {
        if (card == null || card.getLastReviewedAt() == null) {
            return NEW;
        }
        return card.getRepetitionCount() == 0 ? RELEARNING : REVIEW;
    }
}
