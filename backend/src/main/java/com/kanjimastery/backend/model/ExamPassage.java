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

/**
 * Đoạn văn của một đề JLPT kèm các câu hỏi của nó: đoạn 文章の文法 của phần Ngữ pháp (chỗ trống đánh dấu 【1】【2】...)
 * hoặc bài đọc của phần Đọc hiểu - xem V25__add_exam_passages.sql, V40__add_reading_section.sql.
 */
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

    /** Phần thi có đoạn văn này: GRAMMAR (文章の文法) hoặc READING; ghép đề chỉ lấy đoạn văn đúng phần. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ExamSection section;

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

    /** Đoạn văn nào của đề gốc (vd. N3-05/NP/19-23) với đoạn nhập từ file đề; null với nguồn khác. */
    @Column(name = "source_ref", length = 60)
    private String sourceRef;

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
