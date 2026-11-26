package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.VocabularyImportRequest;
import com.kanjimastery.backend.dto.VocabularyImportRequest.Word;
import com.kanjimastery.backend.dto.VocabularyImportResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.model.WordCharacter;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyImportServiceTest {

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private StrokeCounter strokeCounter;

    private VocabularyImportService service;

    private final Tag lesson = Tag.builder().id(9L).name("N2-01").build();
    // 表 đọc おもて đã có ở N4; 見 ghi kiểu cũ: chữ 見 + cách đọc み(る); ある "có" ở N5.
    private final Kanji front = stored(1L, "表", "おもて", "Mặt trước");
    private final Kanji see = stored(2L, "見", "み(る)", "Nhìn");
    private final Kanji exist = stored(3L, "ある", null, "Có (đồ vật)");

    @BeforeEach
    void setUp() {
        service = new VocabularyImportService(kanjiRepository, tagRepository, strokeCounter,
                Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC));
        lenient().when(strokeCounter.lookup(anyCollection()))
                .thenReturn(Map.of("表", 8, "見", 7, "人", 2, "生", 5, "静", 14, "好", 6, "便", 9));
        lenient().when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(front, see, exist));
        lenient().when(tagRepository.findByName("N2-01")).thenReturn(Optional.of(lesson));
    }

    @Test
    void dryRun_shouldSortTheWordsIntoNewAndExisting_withoutWritingAnything() {
        VocabularyImportResponse report = service.importLesson(request(
                word("人生", "じんせい", "nhân sinh", "Cuộc đời"),
                word("見る", "みる", "KIẾN", "Nhìn")), true);

        assertThat(report.errors()).isEmpty();
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.linked()).as("見 + み(る) là 見る").isEqualTo(1);
        assertThat(report.imported()).isFalse();
        verify(kanjiRepository, never()).saveAll(any());
        assertThat(see.getTags()).isEmpty();
    }

    @Test
    void import_shouldAddNewWordsToTheLesson_andLinkTheSameWordsAlreadyStored() {
        VocabularyImportResponse report = service.importLesson(request(
                word("人生", "じんせい", "nhân  sinh", "Cuộc đời"),
                word("見る", "みる", "KIẾN", "Nhìn")), false);

        assertThat(report.imported()).isTrue();
        Kanji created = savedWords().get(0);
        assertThat(created.getCharacter()).isEqualTo("人生");
        assertThat(created.getHanViet()).isEqualTo("NHÂN SINH");
        assertThat(created.getStrokeCount()).as("2 + 5 theo bảng số nét").isEqualTo(7);
        assertThat(created.getJlptLevel()).isEqualTo(JlptLevel.N2);
        assertThat(created.getTags()).containsExactly(lesson);
        assertThat(created.getCharacterParts())
                .containsExactly(new WordCharacter("人", "NHÂN"), new WordCharacter("生", "SINH"));
        assertThat(see.getTags()).containsExactly(lesson);
    }

    @Test
    void sameSpellingWithAnotherReading_shouldBeANewRow_withAWarning() {
        VocabularyImportResponse report = service.importLesson(request(word("表", "ひょう", "BIỂU", "Bảng, biểu")), false);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.linked()).isZero();
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("đồng tự") && warning.contains("おもて"));
        assertThat(savedWords()).extracting(Kanji::getReading).containsExactly("ひょう");
        assertThat(front.getTags()).isEmpty();
    }

    @Test
    void kanaWordsAlreadyStored_shouldBeLinkedWithAWarning_unlessMarkedAsANewMeaning() {
        VocabularyImportResponse linked = service.importLesson(request(word("ある", null, null, "Có")), true);

        assertThat(linked.linked()).isEqualTo(1);
        assertThat(linked.warnings()).anyMatch(warning -> warning.contains("tao_moi"));

        Word anotherMeaning = new Word("ある", null, null, "Nọ, một (ある日)", "連体詞", null, null, true);
        VocabularyImportResponse created = service.importLesson(request(anotherMeaning), true);

        assertThat(created.created()).isEqualTo(1);
        assertThat(created.linked()).isZero();
    }

    @Test
    void reimport_shouldSkipWordsAlreadyInTheLesson() {
        see.getTags().add(lesson);

        VocabularyImportResponse report = service.importLesson(request(word("見る", "みる", "KIẾN", "Nhìn")), false);

        assertThat(report.alreadyInLesson()).isEqualTo(1);
        assertThat(report.linked()).isZero();
        assertThat(report.created()).isZero();
    }

    @Test
    void naAdjectives_shouldLoseTheirNa() {
        Word quiet = new Word("静かな", "しずかな", "TĨNH", "Yên tĩnh", "形容詞", null, null, null);

        service.importLesson(request(quiet), false);

        assertThat(savedWords()).extracting(Kanji::getCharacter, Kanji::getReading)
                .containsExactly(org.assertj.core.api.Assertions.tuple("静か", "しずか"));
    }

    @Test
    void mistakes_shouldBeReportedPerWord_andNothingWritten() {
        VocabularyImportResponse report = service.importLesson(request(
                word("好む", "このみ", "HIẾU", "Thích"),
                word("人生", "じんせい", "NHÂN", "Cuộc đời"),
                word("生む", null, "SINH", "Sinh ra"),
                word("𠮟る", "しかる", "SẤT", "Mắng"),
                word("人", "ひと", "NHÂN", " ")), false);

        assertThat(report.errors()).containsExactly(
                "Từ 1 「好む」: cách đọc このみ không khớp phần kana của 「好む」",
                "Từ 2 「人生」: \"han_viet\" 「NHÂN」 không tách được theo từng chữ Hán (COUNT_MISMATCH) - cần mỗi chữ Hán một âm",
                "Từ 3 「生む」: thiếu \"doc\" (cách đọc hiragana)",
                "Từ 5 「人」: thiếu \"nghia\"",
                "Từ 4 「𠮟る」: chưa có số nét của chữ 𠮟 - ghi \"so_net\" của cả từ");
        assertThat(report.imported()).isFalse();
        verify(kanjiRepository, never()).saveAll(any());
    }

    @Test
    void twoRowsWithTheSameSpelling_shouldNotEndUpInOneLesson() {
        VocabularyImportResponse report = service.importLesson(request(
                word("便", "びん", "TIỆN", "Chuyến"),
                word("便", "べん", "TIỆN", "Tiện lợi")), true);

        assertThat(report.errors()).singleElement().asString()
                .startsWith("Từ 2 「便」: bài N2-01 đã có 「便」 (Từ 1 「便」)");
    }

    @Test
    void aMissingLesson_shouldBeCreated_andABadLessonNameRefused() {
        when(tagRepository.findByName("N2-01")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.importLesson(request(word("人生", "じんせい", "NHÂN SINH", "Cuộc đời")), false);

        assertThat(savedWords().get(0).getTags()).extracting(Tag::getName).containsExactly("N2-01");
        assertThatThrownBy(() -> service.importLesson(new VocabularyImportRequest("N2 01!", "N2", List.of()), true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void readingFits_shouldCheckTheKanaAroundTheKanji() {
        assertThat(VocabularyImportService.readingFits("張り切る", "はりきる")).isTrue();
        assertThat(VocabularyImportService.readingFits("張り切る", "はりきろ")).isFalse();
        assertThat(VocabularyImportService.readingFits("ガス代", "がすだい")).as("katakana so như hiragana").isTrue();
        assertThat(VocabularyImportService.readingFits("人々", "ひとびと")).isTrue();
    }

    @SuppressWarnings("unchecked")
    private List<Kanji> savedWords() {
        ArgumentCaptor<List<Kanji>> captor = ArgumentCaptor.forClass(List.class);
        verify(kanjiRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static VocabularyImportRequest request(Word... words) {
        return new VocabularyImportRequest("N2-01", "N2", List.of(words));
    }

    private static Word word(String word, String reading, String hanViet, String meaning) {
        return new Word(word, reading, hanViet, meaning, null, null, null, null);
    }

    private static Kanji stored(Long id, String character, String reading, String meaning) {
        return Kanji.builder().id(id).character(character).reading(reading).meaning(meaning).hanViet("")
                .strokeCount(1).jlptLevel(JlptLevel.N4).tags(new HashSet<>(Set.of())).characterParts(new ArrayList<>())
                .build();
    }
}
