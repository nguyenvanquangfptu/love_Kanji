package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.WordCharacter;
import com.kanjimastery.backend.service.HanVietSplitter.Problem;
import com.kanjimastery.backend.service.HanVietSplitter.Split;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HanVietSplitterTest {

    @Test
    void split_shouldGiveEachKanjiItsSyllable_inOrder() {
        Split split = HanVietSplitter.split("勉強", "MIỄN CƯỜNG");

        assertThat(split.problem()).isNull();
        assertThat(split.parts()).containsExactly(new WordCharacter("勉", "MIỄN"), new WordCharacter("強", "CƯỜNG"));
    }

    @Test
    void split_shouldSkipTheKana_aroundAndBetweenTheKanji() {
        assertThat(HanVietSplitter.split("食べる", "THỰC").parts()).containsExactly(new WordCharacter("食", "THỰC"));
        assertThat(HanVietSplitter.split("お茶", "TRÀ").parts()).containsExactly(new WordCharacter("茶", "TRÀ"));
        assertThat(HanVietSplitter.split("取り消す", "THỦ TIÊU").parts())
                .containsExactly(new WordCharacter("取", "THỦ"), new WordCharacter("消", "TIÊU"));
    }

    @Test
    void split_shouldCountTheIterationMark_butNotGiveItAPartOfItsOwn() {
        assertThat(HanVietSplitter.split("人々", "NHÂN NHÂN").parts()).containsExactly(new WordCharacter("人", "NHÂN"));
        assertThat(HanVietSplitter.split("人々", "NHÂN").problem()).isEqualTo(Problem.COUNT_MISMATCH);
    }

    @Test
    void split_shouldNormalizeTheHanViet_toOneComposedUppercaseForm() {
        // "ngân" gõ kiểu tổ hợp: n + g + a + dấu mũ (U+0302) + n.
        String decomposed = "ngân  hàng ";

        assertThat(HanVietSplitter.split("銀行", decomposed).parts())
                .containsExactly(new WordCharacter("銀", "NGÂN"), new WordCharacter("行", "HÀNG"));
    }

    @Test
    void split_shouldCountKanjiOutsideTheBasicPlane_asOneCharacter() {
        // 𠮟 (U+20B9F) là hai char Java.
        assertThat(HanVietSplitter.split("𠮟る", "SẤT").parts()).containsExactly(new WordCharacter("𠮟", "SẤT"));
    }

    @Test
    void split_shouldReportWhyAWordCannotBeSplit() {
        assertThat(HanVietSplitter.split("テレビ", "").problem()).isEqualTo(Problem.NO_KANJI);
        assertThat(HanVietSplitter.split("ありがとう", "").parts()).isEmpty();
        assertThat(HanVietSplitter.split("銀行", " ").problem()).isEqualTo(Problem.NO_HAN_VIET);
        assertThat(HanVietSplitter.split("銀行", null).problem()).isEqualTo(Problem.NO_HAN_VIET);
        assertThat(HanVietSplitter.split("銀行", "NGÂN").problem()).isEqualTo(Problem.COUNT_MISMATCH);
        assertThat(HanVietSplitter.split("銀行", "NGÂN HÀNG XÁ").problem()).isEqualTo(Problem.COUNT_MISMATCH);
        assertThat(HanVietSplitter.split("銀行", "NGÂN-HÀNG X").problem()).isEqualTo(Problem.NOT_A_SYLLABLE);
        assertThat(HanVietSplitter.split("銀行", "(NGÂN) HÀNG").problem()).isEqualTo(Problem.NOT_A_SYLLABLE);
        assertThat(HanVietSplitter.split("銀行", "NGÂN HÀNG").parts()).hasSize(2);
    }
}
