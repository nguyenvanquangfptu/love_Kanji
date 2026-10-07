package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Trạng thái SM-2 của một thẻ: số lần nhớ liên tiếp (về 0 khi quên) và hệ số dễ EF (1,30 - 2,50+). Được tính lại sau mỗi
 * lần ôn, kể cả khi lịch ôn theo FSRS, để đổi thuật toán lúc nào cũng có sẵn.
 */
@Embeddable
public record Sm2State(
        @Column(name = "repetition_count") Integer repetitionCount,
        @Column(name = "easiness_factor", precision = 4, scale = 2) BigDecimal easinessFactor) {

    /** Thẻ chưa ôn lần nào. */
    public static Sm2State initial() {
        return new Sm2State(0, new BigDecimal("2.50"));
    }
}
