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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExamAttemptStatus status = ExamAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Buổi làm đề JLPT chứa lượt thi này; null = thi nhanh. */
    @Column(name = "sitting_id")
    private Long sittingId;

    /** Phần của đề JLPT; null = thi nhanh. */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private ExamSection section;

    /** Thời gian làm bài của lượt (giây); null = mặc định {@code app.exam.duration-seconds}. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Lúc kết quả bài thi được đưa vào ôn tập; null = chưa (bài đang làm, hoặc thi trước khi có tính năng này). */
    @Column(name = "diagnosed_at")
    private LocalDateTime diagnosedAt;
}
