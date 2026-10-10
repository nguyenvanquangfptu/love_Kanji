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
import java.util.Optional;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level", nullable = false, length = 5)
    private JlptLevel jlptLevel;

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

    /** Chỉ lấy câu của nguồn này (vd. IMPORTED - các đề tự soạn); null = mọi câu đã duyệt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_source", length = 10)
    private ExamQuestionSource questionSource;

    /** Thời gian tự đặt của từng phần, vd. "VOCABULARY:40,GRAMMAR:30"; phần không có ở đây theo thời gian đề thật. */
    @Column(name = "section_minutes", length = 60)
    private String sectionMinutes;

    public List<ExamSection> sectionList() {
        return Arrays.stream(sections.split(",")).map(ExamSection::valueOf).toList();
    }

    /** Số phút người học tự đặt cho một phần, nếu có. */
    public Optional<Integer> customMinutes(ExamSection section) {
        if (!hasCustomTime()) {
            return Optional.empty();
        }
        return Arrays.stream(sectionMinutes.split(","))
                .map(entry -> entry.split(":"))
                .filter(pair -> pair.length == 2 && pair[0].equals(section.name()))
                .map(pair -> Integer.parseInt(pair[1]))
                .findFirst();
    }

    /** Có phần làm với thời gian tự đặt: kết quả không so được với người làm đúng giờ đề thật. */
    public boolean hasCustomTime() {
        return sectionMinutes != null && !sectionMinutes.isBlank();
    }
}
