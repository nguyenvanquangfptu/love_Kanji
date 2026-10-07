package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Mục tiêu học của một người - xem V14__add_learning_profile.sql. */
@Entity
@Table(name = "user_learning_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** N5..N1; null = chưa chọn. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", length = 5)
    private JlptLevel targetLevel;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "daily_minutes", nullable = false)
    private Integer dailyMinutes;

    /** Người học tự đặt số từ mới mỗi ngày; null = để app tính. */
    @Column(name = "new_words_per_day")
    private Integer newWordsPerDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SchedulerType scheduler = SchedulerType.SM2;

    /** Tỉ lệ nhớ mong muốn khi xếp lịch bằng FSRS. */
    @Column(name = "desired_retention", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal desiredRetention = new BigDecimal("0.90");

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
