package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
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
import java.util.Arrays;
import java.util.List;

/** Một buổi làm đề JLPT gồm một hoặc nhiều phần - xem V22__add_exam_sittings.sql. */
@Entity
@Table(name = "exam_sittings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSitting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "jlpt_level", nullable = false, length = 5)
    private String jlptLevel;

    /** Các phần đã chọn ({@link ExamSection}), cách nhau bởi dấu phẩy, theo thứ tự làm bài. */
    @Column(nullable = false, length = 40)
    private String sections;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExamSittingStatus status = ExamSittingStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public List<ExamSection> sectionList() {
        return Arrays.stream(sections.split(",")).map(ExamSection::valueOf).toList();
    }
}
