package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/** Tham số FSRS tối ưu riêng cho một người - xem V17__add_fsrs_parameters.sql. */
@Entity
@Table(name = "user_fsrs_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FsrsParameters {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "fsrs_version", nullable = false, length = 10)
    private String fsrsVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Double> parameters;

    /** Số từ (lần học đầu + lần ôn kế tiếp) đã dùng để tối ưu. */
    @Column(name = "first_reviews", nullable = false)
    private Integer firstReviews;

    @Column(name = "optimized_at", nullable = false)
    private LocalDateTime optimizedAt;
}
