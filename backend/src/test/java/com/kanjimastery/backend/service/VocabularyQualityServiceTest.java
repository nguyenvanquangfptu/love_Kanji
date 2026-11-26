package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.VocabularyQualityReport;
import com.kanjimastery.backend.dto.VocabularyQualityReport.StrokeMismatch;
import com.kanjimastery.backend.dto.VocabularyQualityReport.WordRef;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyQualityServiceTest {

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private StrokeCounter strokeCounter;

    private VocabularyQualityService service;

    // 外 ghi 9 nét (đúng 5), 時計 đúng 19, 𠮟る chưa có số nét, 込む thiếu Hán Việt.
    private final Kanji outside = word(1L, "外", "NGOẠI", 9);
    private final Kanji clock = word(2L, "時計", "THỜI KẾ", 19);
    private final Kanji scold = word(3L, "𠮟る", "SẤT", 7);
    private final Kanji crowded = word(4L, "込む", "", 5);
    private final Kanji tv = word(5L, "テレビ", "", 0);

    @BeforeEach
    void setUp() {
        service = new VocabularyQualityService(kanjiRepository, strokeCounter);
        lenient().when(strokeCounter.lookup(anyCollection()))
                .thenReturn(Map.of("外", 5, "時", 10, "計", 9, "込", 5));
    }

    @Test
    void report_shouldListWrongStrokeCounts_unknownKanji_andWordsWithoutHanViet() {
        when(kanjiRepository.findAll(any(Sort.class))).thenReturn(List.of(outside, clock, scold, crowded, tv));

        VocabularyQualityReport report = service.report();

        assertThat(report.words()).isEqualTo(5);
        assertThat(report.strokeMismatches()).containsExactly(new StrokeMismatch(1L, "外", 9, 5));
        assertThat(report.unknownCharacters()).containsExactly("𠮟");
        assertThat(report.missingHanViet()).as("テレビ không có chữ Hán nên không cần Hán Việt")
                .containsExactly(new WordRef(4L, "込む", ""));
    }

    @Test
    void report_shouldGroupEachKanjiByItsHanViet_rarestFirst() {
        when(kanjiRepository.findAll(any(Sort.class))).thenReturn(List.of());
        when(kanjiRepository.findCharactersWithSeveralHanViet()).thenReturn(List.of(
                reading("込", "NHẬP", 10L, "申し込む", "THÂN NHẬP"),
                reading("込", "VÀO", 11L, "落ち込む", "LẠC VÀO"),
                reading("込", "VÀO", 12L, "飛び込む", "PHI VÀO")));

        VocabularyQualityReport report = service.report();

        assertThat(report.hanVietConflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.character()).isEqualTo("込");
            assertThat(conflict.variants()).extracting(VocabularyQualityReport.Variant::hanViet, variant -> variant.words().size())
                    .containsExactly(tuple("NHẬP", 1), tuple("VÀO", 2));
            assertThat(conflict.variants().get(0).words()).containsExactly(new WordRef(10L, "申し込む", "THÂN NHẬP"));
        });
    }

    @Test
    void fixStrokeCounts_shouldOnlyCount_whenDryRun_thenRewriteTheWrongOnes() {
        when(kanjiRepository.findAll()).thenReturn(List.of(outside, clock, scold));

        assertThat(service.fixStrokeCounts(true)).isEqualTo(new VocabularyQualityReport.StrokeFix(true, 1));
        assertThat(outside.getStrokeCount()).as("chạy thử không ghi").isEqualTo(9);

        assertThat(service.fixStrokeCounts(false)).isEqualTo(new VocabularyQualityReport.StrokeFix(false, 1));
        assertThat(outside.getStrokeCount()).isEqualTo(5);
        assertThat(scold.getStrokeCount()).as("chữ chưa có số nét: giữ nguyên").isEqualTo(7);
    }

    private static Kanji word(Long id, String character, String hanViet, int strokeCount) {
        return Kanji.builder().id(id).character(character).hanViet(hanViet).strokeCount(strokeCount).meaning("nghĩa")
                .jlptLevel(JlptLevel.N2).build();
    }

    private static KanjiRepository.CharacterReading reading(String character, String hanViet, Long wordId, String word,
                                                            String wordHanViet) {
        return new KanjiRepository.CharacterReading() {
            public String getCharacter() {
                return character;
            }

            public String getHanViet() {
                return hanViet;
            }

            public Long getWordId() {
                return wordId;
            }

            public String getWord() {
                return word;
            }

            public String getWordHanViet() {
                return wordHanViet;
            }
        };
    }
}
