package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SrsCalculatorServiceTest {

    private final SrsCalculatorService calculator = new SrsCalculatorService();

    @Test
    void firstReview_withQuality5_shouldSetIntervalTo1AndIncreaseEf() {
        var result = calculator.calculateNext(0, new BigDecimal("2.50"), 0, 5);

        assertThat(result.repetitionCount()).isEqualTo(1);
        assertThat(result.reviewIntervalDays()).isEqualTo(1);
        // EF' = 2.50 + (0.1 - 0*(0.08 + 0*0.02)) = 2.60
        assertThat(result.easinessFactor()).isEqualByComparingTo("2.60");
    }

    @Test
    void secondReview_afterSuccessfulFirst_shouldSetIntervalTo6() {
        var first = calculator.calculateNext(0, new BigDecimal("2.50"), 0, 5);
        var second = calculator.calculateNext(first.repetitionCount(), first.easinessFactor(), first.reviewIntervalDays(), 4);

        assertThat(second.repetitionCount()).isEqualTo(2);
        assertThat(second.reviewIntervalDays()).isEqualTo(6);
    }

    @Test
    void thirdReview_shouldMultiplyPreviousIntervalByTheNewlyUpdatedEasinessFactor() {
        // q=5 cả 3 lần: EF 2.50 -> 2.60 -> 2.70 -> 2.80 (mỗi lần +0.1 vì q=5)
        // interval: 1 -> 6 -> round(6 * 2.80) = 17
        // Lưu ý: I(n) dùng EF VỪA cập nhật ở chính bước đó, không phải EF của bước trước.
        var first = calculator.calculateNext(0, new BigDecimal("2.50"), 0, 5);
        var second = calculator.calculateNext(first.repetitionCount(), first.easinessFactor(), first.reviewIntervalDays(), 5);
        var third = calculator.calculateNext(second.repetitionCount(), second.easinessFactor(), second.reviewIntervalDays(), 5);

        assertThat(second.easinessFactor()).isEqualByComparingTo("2.70");
        assertThat(third.easinessFactor()).isEqualByComparingTo("2.80");
        assertThat(third.repetitionCount()).isEqualTo(3);
        assertThat(third.reviewIntervalDays()).isEqualTo(17);
    }

    @Test
    void quality_belowThree_shouldResetRepetitionAndInterval_butStillUpdateEf() {
        // Đã học được 3 lần (n=3, EF=2.5, interval=15), nhưng lần này trả lời sai (q=2)
        var result = calculator.calculateNext(3, new BigDecimal("2.50"), 15, 2);

        assertThat(result.repetitionCount()).isZero();
        assertThat(result.reviewIntervalDays()).isEqualTo(1);
        // EF vẫn được cập nhật theo công thức chuẩn dù q < 3 (đúng theo SM-2 gốc)
        // EF' = 2.50 + (0.1 - 3*(0.08 + 3*0.02)) = 2.50 - 0.32 = 2.18
        assertThat(result.easinessFactor()).isEqualByComparingTo("2.18");
    }

    @Test
    void quality_zero_shouldResetAndDecreaseEfSignificantly() {
        var result = calculator.calculateNext(5, new BigDecimal("2.00"), 30, 0);

        assertThat(result.repetitionCount()).isZero();
        assertThat(result.reviewIntervalDays()).isEqualTo(1);
        // EF' = 2.00 + (0.1 - 5*(0.08+5*0.02)) = 2.00 - 0.8 = 1.20 -> chặn ở 1.30
        assertThat(result.easinessFactor()).isEqualByComparingTo("1.30");
    }

    @Test
    void easinessFactor_shouldNeverGoBelowMinimum1_3() {
        var result = calculator.calculateNext(10, new BigDecimal("1.30"), 90, 0);

        assertThat(result.easinessFactor()).isEqualByComparingTo("1.30");
    }

    @Test
    void quality_outOfRange_shouldThrowBadRequest() {
        assertThatThrownBy(() -> calculator.calculateNext(0, new BigDecimal("2.50"), 0, 6))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> calculator.calculateNext(0, new BigDecimal("2.50"), 0, -1))
                .isInstanceOf(BadRequestException.class);
    }
}
