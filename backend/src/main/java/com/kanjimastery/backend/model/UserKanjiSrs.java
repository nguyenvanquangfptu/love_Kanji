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

    @Column(name = "repetition_count")
    @Builder.Default
    private Integer repetitionCount = 0;

    @Column(name = "easiness_factor", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal easinessFactor = new BigDecimal("2.50");

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

    /** Cách nhớ riêng người học tự ghi cho từ này. */
    @Column(name = "personal_note", columnDefinition = "TEXT")
    private String personalNote;
}
