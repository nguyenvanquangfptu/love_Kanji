package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một điểm ngữ pháp trong lịch ôn của người học - xem V38__add_grammar_srs.sql. */
@Entity
@Table(name = "user_grammar_srs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGrammarSrs implements ScheduledCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "grammar_point_id", nullable = false)
    private Long grammarPointId;

    /** Trạng thái SM-2. */
    @Embedded
    @Builder.Default
    private Sm2State sm2 = Sm2State.initial();

    @Column(name = "review_interval_days", nullable = false)
    @Builder.Default
    private Integer reviewIntervalDays = 0;

    @Column(name = "next_review_at", nullable = false)
    private LocalDateTime nextReviewAt;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "lapse_count", nullable = false)
    @Builder.Default
    private Integer lapseCount = 0;

    /** Trí nhớ FSRS; null nếu chưa ôn lần nào. */
    @Embedded
    private FsrsState fsrs;

    /** Khoá lạc quan: hai lần chấm cùng lúc thì lần sau thất bại (409) thay vì ghi đè lần trước. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Override
    public Integer getRepetitionCount() {
        return sm2.repetitionCount();
    }

    @Override
    public BigDecimal getEasinessFactor() {
        return sm2.easinessFactor();
    }

    @Override
    public Double getStability() {
        return fsrs == null ? null : fsrs.stability();
    }

    @Override
    public Double getDifficulty() {
        return fsrs == null ? null : fsrs.difficulty();
    }
}
