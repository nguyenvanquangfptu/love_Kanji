package com.kanjimastery.backend.model;

/** Trạng thái thẻ ôn ngay trước một lần trả lời (cột {@code review_logs.state_before}). */
public enum CardState {
    /** Chưa ôn lần nào (chưa có trong lịch ôn, hoặc vừa được thêm mà chưa lật). */
    NEW,
    /** Đang ôn bình thường. */
    REVIEW,
    /** Vừa quên (SM-2 đã đưa số lần nhớ liên tiếp về 0) và đang học lại. */
    RELEARNING;

    public static CardState of(UserKanjiSrs card) {
        if (card == null || card.getLastReviewedAt() == null) {
            return NEW;
        }
        return card.getRepetitionCount() == 0 ? RELEARNING : REVIEW;
    }
}
