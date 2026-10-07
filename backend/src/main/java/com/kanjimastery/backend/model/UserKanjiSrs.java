package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_kanji_srs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserKanjiSrs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kanji_id", nullable = false)
    private Long kanjiId;

    /** Trạng thái SM-2. */
    @Embedded
    @Builder.Default
    private Sm2State sm2 = Sm2State.initial();

    @Column(name = "review_interval_days")
    @Builder.Default
    private Integer reviewIntervalDays = 0;

    @Column(name = "next_review_at", nullable = false)
    private LocalDateTime nextReviewAt;

    /** Số lần quên sau khi đã học - xem {@link com.kanjimastery.backend.config.SrsProperties#getHardWordLapses()}. */
    @Column(name = "lapse_count", nullable = false)
    @Builder.Default
    private Integer lapseCount = 0;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    /** Trí nhớ FSRS; null nếu chưa ôn lần nào từ khi có FSRS. */
    @Embedded
    private FsrsState fsrs;

    /** Khoá lạc quan: hai lần chấm cùng lúc thì lần sau thất bại (409) thay vì ghi đè lần trước. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** Cách nhớ riêng người học tự ghi cho từ này. */
    @Column(name = "personal_note", columnDefinition = "TEXT")
    private String personalNote;

    public Integer getRepetitionCount() {
        return sm2.repetitionCount();
    }

    public BigDecimal getEasinessFactor() {
        return sm2.easinessFactor();
    }

    /** Null nếu chưa ôn lần nào từ khi có FSRS. */
    public Double getStability() {
        return fsrs == null ? null : fsrs.stability();
    }

    /** Null cùng lúc với {@link #getStability()}. */
    public Double getDifficulty() {
        return fsrs == null ? null : fsrs.difficulty();
    }
}
