package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Suy lại từ được hỏi của câu đã có trong kho, và tìm từ đó trong kho: khớp thẳng, theo cách đọc kana, dạng từ điển. */
class ExamWordMatcherTest {

    @Test
    void hintOf_shouldTakeTheWordFromTheUnderlinedPart_theAnswer_orTheKeyword_asWhenImporting() {
        assertThat(ExamWordMatcher.hintOf(question(JlptQuestionType.KANJI_READING, "延期", "えんき")))
                .isEqualTo(new ExamWordMatcher.Hint("延期", "えんき"));
        assertThat(ExamWordMatcher.hintOf(question(JlptQuestionType.ORTHOGRAPHY, "えんき", "延期")))
                .isEqualTo(new ExamWordMatcher.Hint("延期", "えんき"));
        assertThat(ExamWordMatcher.hintOf(question(JlptQuestionType.CONTEXT, null, "延期")))
                .isEqualTo(new ExamWordMatcher.Hint("延期", null));
        assertThat(ExamWordMatcher.hintOf(question(JlptQuestionType.PARAPHRASE, "延期", "後に回す")))
                .isEqualTo(new ExamWordMatcher.Hint("延期", null));
        ExamQuestion usage = question(JlptQuestionType.USAGE, null, "会議は来週に延期された。");
        usage.setQuestionText("Chọn câu dùng từ 「延期」 đúng nhất.");
        assertThat(ExamWordMatcher.hintOf(usage)).isEqualTo(new ExamWordMatcher.Hint("延期", null));
    }

    @Test
    void hintOf_shouldBeNull_forOtherTypes_orWithoutTheWord() {
        assertThat(ExamWordMatcher.hintOf(question(JlptQuestionType.GRAMMAR_FORM, null, "ために"))).isNull();
        assertThat(ExamWordMatcher.hintOf(question(JlptQuestionType.KANJI_READING, " ", "えんき"))).isNull();
        ExamQuestion usage = question(JlptQuestionType.USAGE, null, "文。");
        usage.setQuestionText("Chọn câu đúng.");
        assertThat(ExamWordMatcher.hintOf(usage)).isNull();
    }

    @Test
    void match_shouldFindAVerbByItsKanji_andPickTheReadingOfAHomograph() {
        Kanji forgive = word(1L, "許", "ゆる(す)");
        Kanji surface = word(2L, "表", "ひょう");
        Kanji front = word(3L, "表", "おもて");
        ExamWordMatcher.Bank bank = bank(forgive, surface, front);

        assertThat(match("許す", null, bank)).isEqualTo(new ExamWordMatcher.Match(List.of(forgive), false));
        assertThat(match("表", "おもて", bank).words()).containsExactly(front);
        assertThat(match("表", "あらわ", bank).words()).as("không khớp cách đọc nào thì giữ cả hai")
                .containsExactlyInAnyOrder(surface, front);
    }

    @Test
    void match_shouldInferTheDictionaryForm_ofConjugatedWords() {
        Kanji angry = word(1L, "怒る", "おこる");
        Kanji contact = word(2L, "連絡する", "れんらくする");
        Kanji poor = word(3L, "貧しい", "まずしい");
        Kanji easy = word(4L, "楽", "らく");
        Kanji easyNa = word(5L, "楽な", "らくな");
        Kanji forgive = word(6L, "許", "ゆる(す)");
        ExamWordMatcher.Bank bank = bank(angry, contact, poor, easy, easyNa, forgive);

        assertThat(match("怒って", null, bank)).isEqualTo(new ExamWordMatcher.Match(List.of(angry), true));
        assertThat(match("連絡", null, bank).words()).containsExactly(contact);
        assertThat(match("貧しかった", null, bank).words()).containsExactly(poor);
        assertThat(match("楽だ", null, bank).words()).as("楽 và 楽な là một từ ghi hai kiểu")
                .containsExactlyInAnyOrder(easy, easyNa);
        assertThat(match("許して", null, bank).words()).containsExactly(forgive);
    }

    @Test
    void match_shouldNotTakeANoun_forAVerbWithTheSameKanji() {
        Kanji story = word(1L, "話", "はなし");

        assertThat(match("話して", null, bank(story)).found()).as("danh từ 話 không phải 話す").isFalse();
        Kanji speak = word(2L, "話す", "はなす");
        assertThat(match("話して", null, bank(story, speak)).words()).containsExactly(speak);
    }

    @Test
    void kanaWords_shouldMatchAWordWrittenInKanji_onlyWhenTheReadingIsUnique() {
        Kanji kind = word(1L, "優しい", "やさしい");
        Kanji thick = word(2L, "濃い", "こい");
        Kanji love = word(3L, "恋", "こい");
        Kanji row = word(4L, "漕ぐ", "こぐ");
        ExamWordMatcher.Bank bank = bank(kind, thick, love);

        assertThat(match("やさしい", null, bank)).isEqualTo(new ExamWordMatcher.Match(List.of(kind), true));
        assertThat(match("こい", null, bank).found()).as("こい: 濃い hay 恋 - không đoán").isFalse();
        assertThat(match("こいで", null, bank).found()).as("không nhận 濃い + で").isFalse();
        assertThat(match("こいで", null, bank(kind, thick, love, row)).words()).containsExactly(row);
    }

    @Test
    void match_shouldGuessNothing_whenTwoFormsOfOneTierAreInTheBank() {
        Kanji go = word(1L, "行", "い(く)");
        Kanji conduct = word(2L, "行う", "おこなう");

        assertThat(match("行って", null, bank(go, conduct)).found()).isFalse();
        assertThat(match("行って", null, bank(go)).words()).containsExactly(go);
    }

    @Test
    void bank_shouldLookUpTheInferredForms_andTheKanaReadings() {
        KanjiRepository repository = mock(KanjiRepository.class);
        when(repository.findAllByCharacterIn(anyCollection())).thenReturn(List.of());
        when(repository.findAllByDisplayReadingIn(anyCollection())).thenReturn(List.of(word(1L, "優しい", "やさしい")));

        ExamWordMatcher.Bank bank = ExamWordMatcher.bank(List.of(new ExamWordMatcher.Hint("怒って", null),
                new ExamWordMatcher.Hint("やさしい", null)), repository);

        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.captor();
        verify(repository).findAllByCharacterIn(keys.capture());
        assertThat(keys.getValue()).contains("怒って", "怒る", "怒", "やさしい");
        ArgumentCaptor<Collection<String>> readings = ArgumentCaptor.captor();
        verify(repository).findAllByDisplayReadingIn(readings.capture());
        assertThat(readings.getValue()).contains("やさしい").doesNotContain("怒る");
        assertThat(bank.byReading()).containsOnlyKeys("やさしい");
    }

    private static ExamWordMatcher.Match match(String written, String reading, ExamWordMatcher.Bank bank) {
        return ExamWordMatcher.match(new ExamWordMatcher.Hint(written, reading), bank);
    }

    /** Kho chỉ có {@code words}, tìm sẵn như {@link ExamWordMatcher#bank} làm. */
    private static ExamWordMatcher.Bank bank(Kanji... words) {
        return new ExamWordMatcher.Bank(
                Arrays.stream(words).collect(Collectors.groupingBy(Kanji::getCharacter)),
                Arrays.stream(words).filter(kanji -> kanji.getReading() != null)
                        .collect(Collectors.groupingBy(kanji -> ReadingMatcher.displayReading(kanji.getReading()))));
    }

    private static Kanji word(long id, String character, String reading) {
        return Kanji.builder().id(id).character(character).reading(reading).build();
    }

    private static ExamQuestion question(JlptQuestionType type, String highlight, String correct) {
        return ExamQuestion.builder().questionType(type).questionText("Đề bài").highlight(highlight)
                .optionA("x").optionB(correct).optionC("y").optionD("z").correctOption("B").build();
    }
}
