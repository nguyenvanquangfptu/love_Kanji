package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_exam_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "jlpt_level", nullable = false, length = 5)
    private String jlptLevel;

    @Column(name = "total_score")
    @Builder.Default
    private Integer totalScore = 0;

    @Column(name = "time_spent_seconds")
    @Builder.Default
    private Integer timeSpentSeconds = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = ExamAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
