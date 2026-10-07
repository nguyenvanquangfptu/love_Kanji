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

/** Một điểm ngữ pháp (〜てから...) của một cấp độ - xem V23__add_grammar_points.sql. */
@Entity
@Table(name = "grammar_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrammarPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level", nullable = false, length = 5)
    private JlptLevel jlptLevel;

    /** Bài trong giáo trình (N4-26...); null = không theo bài. */
    @Column(length = 20)
    private String lesson;

    @Column(nullable = false, length = 100)
    private String pattern;

    /** Cách nối: Vて + から. */
    @Column(length = 200)
    private String connection;

    @Column(name = "meaning_vi", nullable = false, columnDefinition = "TEXT")
    private String meaningVi;

    @Column(name = "explanation_vi", columnDefinition = "TEXT")
    private String explanationVi;
}
