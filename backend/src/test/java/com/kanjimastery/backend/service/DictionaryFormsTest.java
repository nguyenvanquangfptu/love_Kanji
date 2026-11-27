package com.kanjimastery.backend.service;

import com.kanjimastery.backend.service.DictionaryForms.Tier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Đoán dạng từ điển của từ như trong câu - các mẫu lấy từ câu thi chưa gắn được từ trong kho N3 (11/2026). */
class DictionaryFormsTest {

    @ParameterizedTest(name = "{0} -> {1} ({2})")
    @CsvSource({
            // động từ: thể て / た, ている, phủ định, lịch sự, bị động, sai khiến
            "怒って, 怒る, VERB",
            "払って, 払う, VERB",
            "落ち込んで, 落ち込む, VERB",
            "傾いた, 傾く, VERB",
            "かせいで, かせぐ, VERB",
            "散らかした, 散らかす, VERB",
            "枯れて, 枯れる, VERB",
            "見て, 見る, VERB",
            "落ち着いている, 落ち着く, VERB",
            "散らかっている, 散らかる, VERB",
            "守らない, 守る, VERB",
            "繰り返さない, 繰り返す, VERB",
            "省きます, 省く, VERB",
            "叱られた, 叱る, VERB",
            "信じられない, 信じる, VERB",
            "すべらせて, すべる, VERB",
            "奪われた, 奪う, VERB",
            "行って, 行く, VERB",
            "こいで, こぐ, VERB",
            // danh từ する
            "外出して, 外出する, SURU",
            "外出して, 外出, SURU",
            "手配しました, 手配, SURU",
            "にこにこしている, にこにこ, SURU",
            "見学する, 見学, SURU",
            // tính từ đuôi い
            "貧しかった, 貧しい, I_ADJECTIVE",
            "くどくて, くどい, I_ADJECTIVE",
            "詳しく, 詳しい, I_ADJECTIVE",
            // だ / な / に / で, になる
            "楽だ, 楽, COPULA",
            "楽だ, 楽な, COPULA",
            "不注意で, 不注意, COPULA",
            "大幅に, 大幅, COPULA",
            "あいまいだった, あいまい, COPULA",
            "くたくたになる, くたくた, COPULA",
            "連絡, 連絡する, SURU",
            "丁寧, 丁寧な, COPULA",
    })
    void of_shouldOfferTheDictionaryForm_inItsTier(String surface, String dictionaryForm, Tier tier) {
        assertThat(DictionaryForms.of(surface))
                .filteredOn(form -> form.spellings().contains(dictionaryForm))
                .extracting(DictionaryForms.Form::tier)
                .first()
                .isEqualTo(tier);
    }

    @Test
    void verbsShouldComeBeforeNouns_soThatHanashiteIsHanasu() {
        List<DictionaryForms.Form> forms = DictionaryForms.of("話して");

        assertThat(forms.get(0).tier()).isEqualTo(Tier.VERB);
        assertThat(forms).filteredOn(form -> form.tier() == Tier.VERB)
                .flatExtracting(DictionaryForms.Form::spellings).contains("話す");
        assertThat(forms).as("danh từ する có từ hai chữ: 話 + して không phải danh từ 話")
                .flatExtracting(DictionaryForms.Form::spellings).doesNotContain("話", "話する");
    }

    @Test
    void nounsLikeAMasuStem_andAttributives_shouldNotBeTakenForVerbsOrNaAdjectives() {
        for (String noun : List.of("付き合い", "向き", "届け", "誤り", "見直し")) {
            assertThat(DictionaryForms.of(noun)).as(noun).noneMatch(form -> form.tier() == Tier.VERB);
        }
        assertThat(DictionaryForms.of("あて")).flatExtracting(DictionaryForms.Form::spellings).doesNotContain("ある");
        assertThat(DictionaryForms.of("おかしな")).flatExtracting(DictionaryForms.Form::spellings)
                .doesNotContain("おかし");
        assertThat(DictionaryForms.of("大きな")).flatExtracting(DictionaryForms.Form::spellings).doesNotContain("大き");
        assertThat(DictionaryForms.of("わずかな")).flatExtracting(DictionaryForms.Form::spellings).contains("わずか");
    }

    @Test
    void of_shouldNotOfferTheWordItself_orAnythingForAnEmptyWord() {
        assertThat(DictionaryForms.of("怒って")).flatExtracting(DictionaryForms.Form::spellings).doesNotContain("怒って");
        assertThat(DictionaryForms.of(" ")).isEmpty();
        assertThat(DictionaryForms.of(null)).isEmpty();
    }

    @Test
    void naAdjectivesAndSuruNouns_shouldKeepTheirSpellingsTogether_asOneForm() {
        assertThat(DictionaryForms.of("楽だ")).filteredOn(form -> form.spellings().contains("楽"))
                .singleElement().extracting(DictionaryForms.Form::spellings).asList()
                .containsExactly("楽", "楽な", "楽する");
    }
}
