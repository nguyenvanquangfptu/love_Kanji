package com.kanjimastery.backend.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Romaji người học gõ → hiragana: Hepburn, kiểu bàn phím, âm ngắt, ん, trường âm, kana gõ thẳng. */
class RomajiConverterTest {

    @ParameterizedTest
    @CsvSource({
            "shusshin, しゅっしん",
            "syussin, しゅっしん",
            "kitte, きって",
            "matcha, まっちゃ",
            "macchi, まっち",
            "tsukue, つくえ",
            "tukue, つくえ",
            "chizu, ちず",
            "tizu, ちず",
            "fuyu, ふゆ",
            "huyu, ふゆ",
            "jisho, じしょ",
            "zisyo, じしょ",
            "jya, じゃ",
            "kon'ya, こんや",
            "konya, こにゃ",
            "han'i, はんい",
            "konnnichiwa, こんにちわ",
            "hon, ほん",
            "honn, ほん",
            "shinbun, しんぶん",
            "shimbun, しんぶん",
            "semmon, せんもん",
            "denshime-ru, でんしめーる",
            "Tokei, とけい",
            "  ko ko  , ここ",
            "wo, を",
            "dzu, づ",
            "vu, ゔ",
            "xtsu, っ",
    })
    void toHiragana_shouldAcceptHepburnAndKeyboardSpellings(String romaji, String kana) {
        assertThat(RomajiConverter.toHiragana(romaji)).containsExactly(kana);
    }

    /** "nn" trước nguyên âm: kiểu bàn phím (hanni = はんい) hay Hepburn (sannin = さんにん) - nhận cả hai. */
    @ParameterizedTest
    @CsvSource({
            "hanni, はんい, はんに",
            "sannin, さんにん, さんいん",
            "konnichiwa, こんにちわ, こんいちわ",
    })
    void toHiragana_shouldOfferBothReadingsOfDoubleNBeforeAVowel(String romaji, String first, String second) {
        assertThat(RomajiConverter.toHiragana(romaji)).containsExactlyInAnyOrder(first, second);
    }

    @ParameterizedTest
    @CsvSource({
            "しゅっしん, しゅっしん",
            "デンシメール, でんしめーる",
            "でんしメール, でんしめーる",
    })
    void toHiragana_shouldKeepKanaTypedWithAJapaneseKeyboard(String typed, String kana) {
        assertThat(RomajiConverter.toHiragana(typed)).containsExactly(kana);
    }

    @ParameterizedTest
    @CsvSource({
            "tōkyō, 4",
            "sensē, 2",
            "obāsan, 1",
    })
    void toHiragana_shouldOfferEveryReadingOfMacronVowels(String romaji, int readings) {
        assertThat(RomajiConverter.toHiragana(romaji)).hasSize(readings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "kq", "ka2", "abc!", "xyz"})
    void toHiragana_shouldGiveNothing_forTextThatIsNotRomaji(String typed) {
        assertThat(RomajiConverter.toHiragana(typed)).isEmpty();
    }
}
