package com.kanjimastery.backend.service;

import com.kanjimastery.backend.service.ReadingMatcher.Mistake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Chấm cách đọc gõ tay và gọi tên lỗi gõ hay gặp. */
class ReadingMatcherTest {

    @ParameterizedTest
    @CsvSource({
            "shusshin, しゅっしん",
            "しゅっしん, しゅっしん",
            "han'i, はんい",
            "hanni, はんい",
            "denshime-ru, でんしメール",
            "でんしメール, でんしメール",
            "miru, み(る)",
            "ookii, おお(きい)",
            "tōkyō, とうきょう",
            "obāsan, おばあさん",
            "tsuzuku, つづく",
            "chijimu, ちぢむ",
    })
    void match_shouldAcceptTheReading_however_itIsTyped(String typed, String reading) {
        assertThat(ReadingMatcher.match(typed, List.of(reading))).hasValueSatisfying(result -> {
            assertThat(result.correct()).isTrue();
            assertThat(result.mistake()).isNull();
        });
    }

    @Test
    void match_shouldAcceptTheReadingOfAnyEntryWithTheSameSpelling() {
        assertThat(ReadingMatcher.match("hairu", List.of("いれる", "はいる"))).hasValueSatisfying(result ->
                assertThat(result.correct()).isTrue());
    }

    @ParameterizedTest
    @CsvSource({
            "tokyo, とうきょう, LONG_VOWEL",
            "obasan, おばあさん, LONG_VOWEL",
            "denshimeru, でんしメール, LONG_VOWEL",
            "shushin, しゅっしん, SOKUON",
            "kaisha, がいしゃ, DAKUTEN",
            "shiyusshin, しゅっしん, SMALL_KANA",
            "hani, はんい, N_BEFORE_VOWEL",
    })
    void match_shouldMarkNearMissesWrong_andNameTheMistake(String typed, String reading, Mistake mistake) {
        assertThat(ReadingMatcher.match(typed, List.of(reading))).hasValueSatisfying(result -> {
            assertThat(result.correct()).isFalse();
            assertThat(result.mistake()).isEqualTo(mistake);
        });
    }

    @Test
    void match_shouldReportWhatItUnderstood_whenTheAnswerIsSimplyWrong() {
        assertThat(ReadingMatcher.match("yama", List.of("かわ"))).hasValueSatisfying(result -> {
            assertThat(result.correct()).isFalse();
            assertThat(result.typedKana()).isEqualTo("やま");
            assertThat(result.mistake()).isNull();
        });
    }

    @Test
    void match_shouldGiveNothing_forTextThatIsNotARomajiReading() {
        assertThat(ReadingMatcher.match("ka2", List.of("か"))).isEmpty();
        assertThat(ReadingMatcher.match("  ", List.of("か"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "見, み(る), 見る",
            "大, おお(きい), 大きい",
            "出身, しゅっしん, 出身",
            "見る, み(る), 見る",
    })
    void writtenForm_shouldAddTheOkuriganaOfTheReading(String character, String reading, String written) {
        assertThat(ReadingMatcher.writtenForm(character, reading)).isEqualTo(written);
    }

    @Test
    void displayReading_shouldDropTheBracketsButKeepKatakana() {
        assertThat(ReadingMatcher.displayReading("み(る)")).isEqualTo("みる");
        assertThat(ReadingMatcher.displayReading("でんしメール")).isEqualTo("でんしメール");
    }
}
