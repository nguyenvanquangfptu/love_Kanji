package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "jlpt_level", nullable = false, length = 5)
    private String jlptLevel;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** {@link ExamQuestionStatus}; câu hỏi của đoạn văn luôn cùng trạng thái. */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String status = ExamQuestionStatus.DRAFT;

    /** {@link ExamQuestionSource} */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String source = ExamQuestionSource.MANUAL;

    /** {@link ExamQuestionFlag}: cảnh báo nặng nhất trong các câu hỏi của đoạn. */
    @Column(length = 20)
    private String flag;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
