package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** Một lần ôn một điểm ngữ pháp - xem V38__add_grammar_srs.sql. */
@Entity
@Immutable
@Table(name = "grammar_review_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrammarReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "grammar_point_id", nullable = false)
    private Long grammarPointId;

    /** Câu hỏi đã dùng; null với thẻ lật (điểm chưa có câu đã duyệt). */
    @Column(name = "question_id")
    private Long questionId;

    /** A-D; null với thẻ lật. */
    @Column(name = "chosen_option", length = 1)
    private String chosenOption;

    @Column(nullable = false)
    private Boolean correct;

    @Convert(converter = ReviewRatingConverter.class)
    @Column(nullable = false)
    private ReviewRating rating;

    @Column(name = "response_ms")
    private Integer responseMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_before", nullable = false, length = 12)
    private CardState stateBefore;

    /** Xác suất FSRS còn nhớ ngay trước lần trả lời; null nếu chưa ôn lần nào. */
    @Column
    private Double retrievability;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
