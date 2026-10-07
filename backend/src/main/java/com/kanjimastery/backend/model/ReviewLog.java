package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một lần người học trả lời một từ - xem V10__add_review_logs.sql. Chỉ ghi thêm, không bao giờ sửa: Hibernate không phát
 * UPDATE cho entity này.
 */
@Entity
@Immutable
@Table(name = "review_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kanji_id", nullable = false)
    private Long kanjiId;

    /** {@link ReviewSource} */
    @Column(nullable = false, length = 10)
    private String source;

    /** {@link QuizDirection}; null với thẻ ôn tập. */
    @Column(length = 20)
    private String direction;

    @Column(nullable = false)
    private Boolean correct;

    /** {@link ReviewRating} */
    @Column(nullable = false)
    private Short rating;

    @Column(name = "response_ms")
    private Integer responseMs;

    @Column(name = "chosen_answer", columnDefinition = "TEXT")
    private String chosenAnswer;

    /** {@link CardState} */
    @Column(name = "state_before", nullable = false, length = 12)
    private String stateBefore;

    @Column(name = "ef_before", precision = 4, scale = 2)
    private BigDecimal efBefore;

    @Column(name = "interval_before")
    private Integer intervalBefore;

    /** Lần trả lời này có được tính vào lịch ôn không (trắc nghiệm đúng khi thẻ chưa đến hạn thì không). */
    @Column(nullable = false)
    private Boolean scheduled;

    /** Xác suất nhớ FSRS dự đoán lúc trả lời; null nếu từ chưa từng được ôn. */
    @Column(name = "retrievability")
    private Double retrievability;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
