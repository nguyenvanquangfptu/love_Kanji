package com.kanjimastery.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một thẻ ôn theo lịch (từ vựng, điểm ngữ pháp) dưới góc nhìn của bộ tính lịch
 * {@link com.kanjimastery.backend.service.CardScheduler}: trạng thái SM-2, trí nhớ FSRS, ngày đến hạn và số lần quên.
 */
public interface ScheduledCard {

    Integer getRepetitionCount();

    BigDecimal getEasinessFactor();

    Integer getReviewIntervalDays();

    LocalDateTime getNextReviewAt();

    /** Null nếu chưa ôn lần nào. */
    LocalDateTime getLastReviewedAt();

    Integer getLapseCount();

    /** Null nếu chưa ôn lần nào từ khi có FSRS. */
    Double getStability();

    /** Null cùng lúc với {@link #getStability()}. */
    Double getDifficulty();

    void setSm2(Sm2State sm2);

    void setReviewIntervalDays(Integer reviewIntervalDays);

    void setNextReviewAt(LocalDateTime nextReviewAt);

    void setLastReviewedAt(LocalDateTime lastReviewedAt);

    void setLapseCount(Integer lapseCount);

    void setFsrs(FsrsState fsrs);
}
