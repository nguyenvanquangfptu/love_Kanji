package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Đoạn văn của 問題3 文章の文法, chỗ trống đánh dấu 【1】【2】... - xem V25__add_exam_passages.sql. */
@Entity
@Table(name = "exam_passages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamPassage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level", nullable = false, length = 5)
    private JlptLevel jlptLevel;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Trạng thái duyệt; đổi qua các phương thức của {@link ReviewState}. */
    @Embedded
    @Builder.Default
    private ReviewState review = new ReviewState(ExamQuestionStatus.DRAFT);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ExamQuestionSource source = ExamQuestionSource.MANUAL;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public ExamQuestionStatus getStatus() {
        return review.getStatus();
    }

    public ExamQuestionFlag getFlag() {
        return review.getFlag();
    }

    public String getReviewNote() {
        return review.getReviewNote();
    }

    public LocalDateTime getReviewedAt() {
        return review.getReviewedAt();
    }
}
