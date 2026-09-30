package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cài đặt thuật toán SuperMemo SM-2 (Piotr Wozniak).
 * EF được cập nhật theo công thức chuẩn cho MỌI giá trị q (0-5); repetition
 * count và interval chỉ reset về (0, 1 ngày) khi q &lt; 3 (trả lời sai/quên).
 */
@Service
public class SrsCalculatorService {

    private static final BigDecimal MIN_EASINESS_FACTOR = new BigDecimal("1.3");

    public SrsResult calculateNext(int repetitionCount, BigDecimal easinessFactor, int reviewIntervalDays, int quality) {
        if (quality < 0 || quality > 5) {
            throw new BadRequestException("Điểm đánh giá (quality) phải nằm trong khoảng 0-5");
        }

        double ef = easinessFactor.doubleValue();
        double rawNewEf = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        BigDecimal newEf = BigDecimal.valueOf(rawNewEf).setScale(2, RoundingMode.HALF_UP);
        if (newEf.compareTo(MIN_EASINESS_FACTOR) < 0) {
            newEf = MIN_EASINESS_FACTOR;
        }

        int newRepetitionCount;
        int newIntervalDays;

        if (quality < 3) {
            newRepetitionCount = 0;
            newIntervalDays = 1;
        } else {
            newRepetitionCount = repetitionCount + 1;
            if (newRepetitionCount == 1) {
                newIntervalDays = 1;
            } else if (newRepetitionCount == 2) {
                newIntervalDays = 6;
            } else {
                newIntervalDays = (int) Math.round(reviewIntervalDays * newEf.doubleValue());
            }
        }

        return new SrsResult(newRepetitionCount, newEf, newIntervalDays);
    }

    public record SrsResult(int repetitionCount, BigDecimal easinessFactor, int reviewIntervalDays) {
    }
}
