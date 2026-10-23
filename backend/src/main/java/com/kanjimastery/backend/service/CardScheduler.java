package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.FsrsState;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ScheduledCard;
import com.kanjimastery.backend.model.Sm2State;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Bộ tính lịch ôn dùng chung cho mọi loại thẻ (từ vựng, ngữ pháp): sau mỗi lần trả lời, SM-2 và trí nhớ FSRS luôn được
 * tính cùng nhau, khoảng ôn lấy theo thuật toán người học đang dùng, thẻ đến hạn từ đầu ngày học (4 giờ sáng).
 * Không đọc hay ghi DB - nơi gọi lo lưu thẻ và ghi lịch sử ôn.
 */
@Component
@RequiredArgsConstructor
public class CardScheduler {

    private final SrsCalculatorService srsCalculatorService;
    private final StudyCalendar calendar;

    /**
     * Kết quả của một mức đánh giá: SM-2 và trí nhớ FSRS luôn được tính cùng nhau (đổi thuật toán lúc nào cũng có sẵn
     * trạng thái), khoảng ôn lấy theo thuật toán người học đang dùng - FSRS thì theo tỉ lệ nhớ mong muốn.
     */
    public record Outcome(SrsCalculatorService.SrsResult sm2, Fsrs.Memory memory, int intervalDays) {
    }

    /**
     * Ghi kết quả của một lần trả lời vào thẻ (chưa lưu). "Quên" một thẻ đang ôn bình thường (đã nhớ lại được kể từ
     * lần quên trước) được đếm vào số lần quên; quên tiếp khi đang học lại thì không tính thêm.
     *
     * @param card thẻ ngay trước lần trả lời; thẻ vừa tạo (chưa ôn lần nào) được tính như từ mới
     */
    public void apply(ScheduledCard card, ReviewRating rating, LocalDateTime now, SchedulingSettings settings) {
        boolean lapse = rating == ReviewRating.AGAIN && CardState.REVIEW.equals(CardState.of(card));
        Outcome outcome = outcome(card, rating, now, settings);

        card.setSm2(new Sm2State(outcome.sm2().repetitionCount(), outcome.sm2().easinessFactor()));
        card.setReviewIntervalDays(outcome.intervalDays());
        // Đến hạn từ đầu ngày học thứ N (4 giờ sáng), không phải đúng giờ của lần ôn này: hôm đến hạn học sớm hơn hôm nay
        // vài tiếng thì thẻ vẫn có trong phiên, không bị đẩy thêm một ngày.
        card.setNextReviewAt(calendar.startOf(calendar.dayOf(now).plusDays(outcome.intervalDays())));
        card.setLastReviewedAt(now);
        card.setFsrs(new FsrsState(outcome.memory().stability(), outcome.memory().difficulty()));
        if (lapse) {
            card.setLapseCount(card.getLapseCount() + 1);
        }
    }

    /** @param card null = từ chưa có trong lịch ôn */
    public Outcome outcome(ScheduledCard card, ReviewRating rating, LocalDateTime now, SchedulingSettings settings) {
        SrsCalculatorService.SrsResult sm2 = srsCalculatorService.calculateNext(
                card == null ? 0 : card.getRepetitionCount(),
                card == null ? new BigDecimal("2.50") : card.getEasinessFactor(),
                card == null ? 0 : card.getReviewIntervalDays(),
                rating.sm2Quality());
        if (rating == ReviewRating.AGAIN && forgottenEarlierToday(card, now)) {
            // SM-2 đã trừ hệ số dễ (EF) ở lần quên đầu tiên trong ngày. Quên tiếp trong cùng ngày học thì không trừ thêm,
            // giống cách đếm số lần quên: trắc nghiệm hay hỏi lại từ vừa sai, và mỗi lần sai trừ 0,54 thì vài câu sai liền
            // nhau kéo EF xuống mức sàn 1,3 - khoảng ôn về sau chỉ còn nhân 1,3, phải ôn dày gấp mấy lần mãi về sau.
            sm2 = new SrsCalculatorService.SrsResult(sm2.repetitionCount(), card.getEasinessFactor(),
                    sm2.reviewIntervalDays());
        }
        Fsrs fsrs = settings.fsrs();
        Fsrs.Memory before = memoryBefore(card, fsrs);
        Fsrs.Memory memory = before == null ? fsrs.first(rating.value())
                : fsrs.next(before, rating.value(), elapsedDays(card, now));
        int intervalDays = settings.usesFsrs()
                ? fsrs.interval(memory.stability(), settings.desiredRetention())
                : sm2.reviewIntervalDays();
        return new Outcome(sm2, memory, intervalDays);
    }

    /** Số ngày tới lần ôn sau nếu chấm Quên, Khó, Nhớ, Dễ - hiện ngay trên các nút chấm. */
    public List<Integer> previewIntervals(ScheduledCard card, LocalDateTime now, SchedulingSettings settings) {
        return Arrays.stream(ReviewRating.values())
                .map(rating -> outcome(card, rating, now, settings).intervalDays())
                .toList();
    }

    /** Xác suất FSRS còn nhớ thẻ lúc {@code now}; null nếu chưa học lần nào. */
    public Double recall(ScheduledCard card, LocalDateTime now, Fsrs fsrs) {
        Fsrs.Memory memory = memoryBefore(card, fsrs);
        return memory == null ? null : fsrs.retrievability(memory.stability(), elapsedDays(card, now));
    }

    /**
     * Trí nhớ FSRS của thẻ ngay trước lần trả lời; null nếu từ chưa từng được ôn. Thẻ đã ôn từ trước khi có FSRS thì
     * ước lượng từ SM-2: vừa quên thì như một thẻ mới quên; còn lại độ ổn định bằng khoảng ôn hiện tại (SM-2 xếp lịch
     * quanh mức nhớ 90%, đúng nghĩa độ ổn định của FSRS) và độ khó suy từ hệ số dễ EF (2,5 như thẻ "Nhớ", 1,3 là 10).
     */
    private Fsrs.Memory memoryBefore(ScheduledCard card, Fsrs fsrs) {
        if (card == null || card.getLastReviewedAt() == null) {
            return null;
        }
        if (card.getStability() != null && card.getDifficulty() != null) {
            return new Fsrs.Memory(card.getStability(), card.getDifficulty());
        }
        if (card.getRepetitionCount() == 0) {
            return fsrs.first(ReviewRating.AGAIN.value());
        }
        double easiest = fsrs.first(ReviewRating.GOOD.value()).difficulty();
        double hardness = (2.5 - card.getEasinessFactor().doubleValue()) / (2.5 - 1.3);
        double difficulty = Math.min(Math.max(easiest + hardness * (10 - easiest), 1), 10);
        return new Fsrs.Memory(Math.max(card.getReviewIntervalDays(), 1), difficulty);
    }

    /** Thẻ đang học lại (đã quên, chưa nhớ lại được) và lần trả lời trước nằm trong cùng ngày học với {@code now}. */
    private boolean forgottenEarlierToday(ScheduledCard card, LocalDateTime now) {
        return CardState.RELEARNING.equals(CardState.of(card)) && elapsedDays(card, now) == 0;
    }

    /** Số ngày học kể từ lần ôn trước (cùng ngày học = 0). */
    private long elapsedDays(ScheduledCard card, LocalDateTime now) {
        return Math.max(0, ChronoUnit.DAYS.between(calendar.dayOf(card.getLastReviewedAt()), calendar.dayOf(now)));
    }
}
