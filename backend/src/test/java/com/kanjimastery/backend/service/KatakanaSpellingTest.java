package com.kanjimastery.backend.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KatakanaSpellingTest {

    @Test
    void toHiragana_shouldKeepTheLongVowelMark() {
        assertThat(KatakanaSpelling.toHiragana("テレビ")).isEqualTo("てれび");
        assertThat(KatakanaSpelling.toHiragana("コーヒー")).isEqualTo("こーひー");
        assertThat(KatakanaSpelling.toHiragana("シャツ")).isEqualTo("しゃつ");
        assertThat(KatakanaSpelling.toHiragana("ベッド")).isEqualTo("べっど");
    }

    @Test
    void isKatakanaWord_shouldTakeOnlyWholeKatakanaWords() {
        assertThat(KatakanaSpelling.isKatakanaWord("テレビ")).isTrue();
        assertThat(KatakanaSpelling.isKatakanaWord("ボールペン")).isTrue();
        assertThat(KatakanaSpelling.isKatakanaWord("テレビ台")).isFalse();
        assertThat(KatakanaSpelling.isKatakanaWord("ペ")).isFalse();
        assertThat(KatakanaSpelling.isKatakanaWord("ーラ")).isFalse();
        assertThat(KatakanaSpelling.isKatakanaWord("ッテ")).isFalse();
        assertThat(KatakanaSpelling.isKatakanaWord(null)).isFalse();
    }

    @Test
    void standsAlone_shouldRejectWordsInsideLongerKatakanaWords() {
        assertThat(KatakanaSpelling.standsAlone("ペンで書きます。", "ペン")).isTrue();
        assertThat(KatakanaSpelling.standsAlone("テレビでニュースを見る。", "テレビ")).isTrue();
        assertThat(KatakanaSpelling.standsAlone("ボールペンで書きます。", "ペン")).isFalse();
        // Hai lần trong câu: không biết gạch chân chỗ nào.
        assertThat(KatakanaSpelling.standsAlone("ペンとペン", "ペン")).isFalse();
    }

    @Test
    void allMisspellings_shouldMakeTheMistakesLearnersMake() {
        assertThat(KatakanaSpelling.allMisspellings("シャツ"))
                // シ/ツ giống hình, thiếu ゛, chữ nhỏ viết to, thừa ー.
                .contains("ツャツ", "シャシ", "ジャツ", "シヤツ", "シャーツ")
                // Không thêm ー trước chữ nhỏ; không bao giờ có từ đúng.
                .doesNotContain("シーャツ", "シャツ");
        assertThat(KatakanaSpelling.allMisspellings("ベッド")).contains("ベド", "ベツド", "ペッド", "ヘッド", "ベット");
        assertThat(KatakanaSpelling.allMisspellings("コーヒー"))
                .contains("コヒー", "コーヒ", "ゴーヒー", "コーピー", "ユーヒー")
                .doesNotContain("コーヒー");
        assertThat(KatakanaSpelling.allMisspellings("バス")).contains("バッス", "バース", "パス", "ハス", "バヌ");
        // Sau ー thì không thêm ッ.
        assertThat(KatakanaSpelling.allMisspellings("コート")).doesNotContain("コーット");
    }

    @RepeatedTest(5)
    void misspellings_shouldPickDistinctWrongSpellings_eachOfAnotherKindWhenTheWordAllows() {
        // Các cách viết sai của シャツ theo kiểu lỗi.
        Map<String, String> kinds = Map.of("ツャツ", "giống hình", "シャシ", "giống hình", "ジャツ", "dấu ゛",
                "シャヅ", "dấu ゛", "シャーツ", "trường âm", "シヤツ", "chữ nhỏ");

        List<String> picked = KatakanaSpelling.misspellings("シャツ", 3);

        assertThat(picked).hasSize(3).doesNotHaveDuplicates().allSatisfy(spelling ->
                assertThat(kinds).containsKey(spelling));
        assertThat(picked.stream().map(kinds::get).distinct()).hasSize(3);
    }

    @Test
    void misspellings_shouldReturnFewer_whenTheWordHasFewWrongSpellings() {
        assertThat(KatakanaSpelling.misspellings("ゼロ", 10)).hasSizeLessThan(10).doesNotContain("ゼロ");
    }
}
