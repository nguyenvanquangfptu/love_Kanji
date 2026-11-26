package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Số nét của một chữ Hán (bảng kanji_strokes, nạp từ KANJIDIC2 - xem V41__add_kanji_strokes.sql). Chỉ đọc. */
@Entity
@Table(name = "kanji_strokes")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KanjiStroke {

    @Id
    @Column(length = 1)
    private String character;

    @Column(name = "stroke_count", nullable = false)
    private int strokeCount;
}
