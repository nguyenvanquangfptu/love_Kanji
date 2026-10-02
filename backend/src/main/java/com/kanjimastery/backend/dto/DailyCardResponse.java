package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.dto.KanjiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DailyCardResponse {
    private Long srsId;
    private KanjiResponse kanji;
    private Integer repetitionCount;
    private BigDecimal easinessFactor;
    private Integer reviewIntervalDays;
    private LocalDateTime nextReviewAt;
    private LocalDateTime lastReviewedAt;
    private Integer lapseCount;
    /** Quên đủ nhiều lần để thành từ khó - hiện nhãn "Từ khó". */
    private boolean hardWord;
    private String personalNote;
    /** Từ mới, chưa học lần nào. */
    private boolean newCard;
    /** Số ngày tới lần ôn sau nếu chấm Quên, Khó, Nhớ, Dễ - theo thuật toán lịch ôn người học đang dùng. */
    private List<Integer> intervals;
}
