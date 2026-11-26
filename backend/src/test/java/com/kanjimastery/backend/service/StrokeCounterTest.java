package com.kanjimastery.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Số nét của một từ = tổng số nét các chữ Hán, theo bảng số nét từng chữ. */
class StrokeCounterTest {

    private static final Map<String, Integer> STROKES = Map.of("時", 10, "計", 9, "人", 2, "々", 3, "込", 5, "落", 12);

    @Test
    void count_shouldAddUpTheKanji_andSkipKana() {
        assertThat(StrokeCounter.count("時計", STROKES)).isEqualTo(new StrokeCounter.Count(19, List.of()));
        assertThat(StrokeCounter.count("落ち込む", STROKES).total()).isEqualTo(17);
    }

    @Test
    void count_shouldCountTheIterationMark_asThreeStrokes() {
        assertThat(StrokeCounter.count("人々", STROKES).total()).isEqualTo(5);
    }

    @Test
    void count_shouldBeZero_forKanaOnlyWords() {
        StrokeCounter.Count count = StrokeCounter.count("ほどく", STROKES);

        assertThat(count.known()).isTrue();
        assertThat(count.total()).isZero();
    }

    @Test
    void count_shouldNameTheKanjiWithoutAStrokeCount_onceEach() {
        StrokeCounter.Count count = StrokeCounter.count("𠮟る𠮟", STROKES);

        assertThat(count.known()).isFalse();
        assertThat(count.unknown()).containsExactly("𠮟");
    }

    @Test
    void kanjiOf_shouldListEachKanjiOnce_countingCharactersOutsideTheBasicPlane() {
        assertThat(StrokeCounter.kanjiOf("時々時計𠮟る")).containsExactly("時", "々", "計", "𠮟");
    }
}
