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

import java.time.LocalDateTime;

/** Thống kê một câu từ kết quả thi thật, do job phân tích câu tính - xem V28__add_question_stats.sql. */
@Entity
@Table(name = "exam_question_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestionStats {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    /** Số lượt trả lời (không tính bỏ trống) từ lần duyệt câu gần nhất. */
    @Column(nullable = false)
    private Integer responses;

    @Column(name = "correct_rate", nullable = false)
    private Double correctRate;

    /** Tỉ lệ đúng nhóm làm tốt các câu khác trừ nhóm làm kém; null khi không chia được hai nhóm. */
    private Double discrimination;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
