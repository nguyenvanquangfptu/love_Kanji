package com.kanjimastery.backend.service;

import com.kanjimastery.backend.service.TextSimilarity.Fingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Độ giống của hai đoạn chữ tiếng Nhật, theo các cặp ký tự liền nhau. */
class TextSimilarityTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "これは（　　）です。                  | これはです",
            "今日は ＿＿＿ ＿＿＿ ＿★＿ ＿＿＿ 。 | 今日は",
            "ＡＢＣ　１２３、ｶﾀｶﾅ                 | ABC123カタカナ",
            "【１】、〔下線〕ごみ〔/下線〕は【2a】  | ごみは",
            "人々がラーメンを食べる。              | 人々がラーメンを食べる",
    })
    void normalize_shouldKeepOnlyLettersAndDigits_inOneWidth(String text, String normalized) {
        assertThat(TextSimilarity.normalize(text)).isEqualTo(normalized);
    }

    @Test
    void normalize_shouldGiveAnEmptyText_forNothingToCompare() {
        assertThat(TextSimilarity.normalize(null)).isEmpty();
        assertThat(TextSimilarity.normalize("（　　）、。★")).isEmpty();
    }

    @Test
    void textsDifferingOnlyInPunctuationWidthOrBlanks_shouldBeTheSame() {
        assertThat(TextSimilarity.similarity("雨が降ったので、試合は来週に延期された。", "雨が降ったので試合は来週に延期された"))
                .isEqualTo(1.0);
        assertThat(TextSimilarity.similarity("ﾃｽﾄは１０時から始まる。", "テストは10時から始まる")).isEqualTo(1.0);
    }

    @Test
    void aSentenceWithOneWordChanged_shouldStayClose() {
        // 台風のため → 雨のため: 16 và 15 cặp ký tự, chung 14 cặp.
        assertThat(TextSimilarity.similarity("台風のため、運動会は来週に延期された。", "雨のため、運動会は来週に延期された。"))
                .isCloseTo(14.0 / 17, within(1e-9));
    }

    @Test
    void unrelatedSentences_shouldBeFarApart() {
        assertThat(TextSimilarity.similarity("毎朝コーヒーを飲んでから会社へ行きます。", "来週の試験に向けて図書館で勉強するつもりだ。"))
                .isLessThan(0.1);
    }

    @Test
    void similarity_shouldNotDependOnTheOrderOfTheTwoTexts() {
        String first = "子どものころは、この川でよく泳いだものです。";
        String second = "子どものころは、よくこの川で泳いだものだ。";

        assertThat(TextSimilarity.similarity(first, second)).isEqualTo(TextSimilarity.similarity(second, first));
    }

    @Test
    void shortTexts_shouldOnlyMatchWhenTheSame() {
        assertThat(TextSimilarity.similarity("毎日走る。", "毎日走る")).isEqualTo(1.0);
        // 5 và 4 ký tự: chung gần hết cặp ký tự nhưng quá ngắn để nói là gần trùng.
        assertThat(TextSimilarity.similarity("毎日走った", "毎日走る")).isZero();
    }

    @Test
    void emptyTexts_shouldNeverMatch() {
        assertThat(TextSimilarity.similarity("", "")).isZero();
        assertThat(TextSimilarity.similarity("。", "（　　）")).isZero();
        assertThat(TextSimilarity.similarity(null, "雨が降ったので、試合は来週に延期された。")).isZero();
    }

    @Test
    void fingerprint_shouldCountKanjiOutsideTheBasicPlaneAsOneCharacter() {
        Fingerprint fingerprint = Fingerprint.of("𠮟る");

        assertThat(fingerprint.length()).isEqualTo(2);
        assertThat(fingerprint.bigrams()).containsExactly("𠮟る");
    }
}
