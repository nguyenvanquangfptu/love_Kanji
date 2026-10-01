package com.kanjimastery.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QuizDistractorGeneratorTest {

    private final QuizDistractorGenerator generator = new QuizDistractorGenerator();

    @Test
    void lookAlikeSpellings_shouldSwapAKanjiForOneThatLooksAlike_andKeepTheKana() {
        List<String> spellings = generator.lookAlikeSpellings("待つ");

        assertThat(spellings).contains("持つ", "特つ", "侍つ").doesNotContain("待つ");
        assertThat(spellings).allSatisfy(spelling -> assertThat(spelling).endsWith("つ"));
    }

    @Test
    void lookAlikeSpellings_shouldTrySingleSwapsBeforeDoubleSwaps() {
        List<String> spellings = generator.lookAlikeSpellings("検査");

        List<Integer> changedChars = spellings.stream().map(spelling -> differingChars("検査", spelling)).toList();
        int singles = (int) changedChars.stream().filter(count -> count == 1).count();
        assertThat(singles).isGreaterThan(0);
        assertThat(changedChars.subList(0, singles)).containsOnly(1);
        assertThat(changedChars.subList(singles, changedChars.size())).containsOnly(2);
    }

    @Test
    void lookAlikeSpellings_shouldAlternatePositions_soChoicesDoNotAllMissTheSameKanji() {
        List<String> firstTwo = generator.lookAlikeSpellings("検査").subList(0, 2);

        assertThat(firstTwo).anySatisfy(spelling -> assertThat(spelling).startsWith("検"));
        assertThat(firstTwo).anySatisfy(spelling -> assertThat(spelling).endsWith("査"));
    }

    @Test
    void lookAlikeSpellings_shouldBeEmpty_whenNoKanjiHasALookAlike() {
        assertThat(generator.lookAlikeSpellings("飛ぶ")).isEmpty();
        assertThat(generator.lookAlikeSpellings("とても")).isEmpty();
    }

    @Test
    void trapReadings_shouldCoverLongVowelSokuonVoicingAndYouonTraps() {
        assertThat(generator.trapReadings("しゅうへん", "周辺")).contains("しゅへん", "しょうへん", "しゅうべん");
        assertThat(generator.trapReadings("がっこう", "学校")).contains("がこう", "がつこう", "がっこ");
        assertThat(generator.trapReadings("ちこく", "遅刻")).contains("ちっこく");
        assertThat(generator.trapReadings("どりょく", "努力")).contains("どうりょく");
        assertThat(generator.trapReadings("びょういん", "病院")).contains("びよういん", "びょいん", "ぴょういん");
        assertThat(generator.trapReadings("せいこう", "成功")).contains("せこう", "ぜいこう", "せいごう");
        assertThat(generator.trapReadings("もくてき", "目的")).contains("もってき");
    }

    @Test
    void trapReadings_shouldPutOneOfEachTrapKindFirst_andAddingNOnlyAfterThem() {
        List<String> traps = generator.trapReadings("かんたん", "簡単");

        // かんたん chỉ bẫy được bằng âm đục; bớt ん (かたん) chỉ dùng sau các kiểu chính.
        assertThat(traps).contains("かたん", "がんたん", "かんだん");
        assertThat(traps.indexOf("かたん")).isGreaterThan(traps.indexOf("がんたん"));
    }

    @Test
    void trapReadings_shouldOnlyInsertSoundsWhereAKanjiReadingCouldEnd() {
        // め|ん, せ|つ, こ|く cùng một chữ: không có めいんせつ, めんせいつ, ちこうく, めんせっつ, ちこっく.
        assertThat(generator.trapReadings("めんせつ", "面接")).doesNotContain("めいんせつ", "めんせいつ", "めんせっつ");
        assertThat(generator.trapReadings("ちこく", "遅刻")).doesNotContain("ちこうく", "ちこっく");
    }

    @Test
    void trapReadings_shouldNotInsertSounds_intoKunReadings() {
        assertThat(generator.trapReadings("あらわれる", "表れる")).isEmpty();
        assertThat(generator.trapReadings("くやしい", "悔しい")).containsExactly("ぐやしい");
    }

    @Test
    void trapReadings_shouldLeaveOkuriganaAndKanaWrittenInTheWordAlone() {
        assertThat(generator.trapReadings("うめる", "埋める")).allSatisfy(reading -> assertThat(reading).endsWith("める"));
        assertThat(generator.trapReadings("とくに", "特に"))
                .isNotEmpty()
                .allSatisfy(reading -> assertThat(reading).endsWith("に"));
        assertThat(generator.trapReadings("おちゃ", "お茶")).allSatisfy(reading -> assertThat(reading).startsWith("お"));
    }

    @Test
    void trapReadings_shouldKeepTheParenthesizedEnding() {
        assertThat(generator.trapReadings("た(べる)", "食"))
                .contains("だ(べる)")
                .allSatisfy(reading -> assertThat(reading).endsWith("(べる)"));
    }

    @Test
    void trapReadings_shouldNeverReturnTheCorrectReading_orImpossibleKana() {
        for (String[] word : new String[][]{
                {"しゅうへん", "周辺"}, {"せっきょくてき", "積極的"}, {"りょこう", "旅行"}, {"きゃく", "客"},
                {"ちゅうい", "注意"}, {"しゅっぱつ", "出発"}, {"つち", "土"}, {"いがい", "意外"}}) {
            assertThat(generator.trapReadings(word[0], word[1]))
                    .doesNotContain(word[0])
                    .allSatisfy(reading -> assertThat(QuizDistractorGenerator.isPlausibleReading(reading))
                            .as(reading).isTrue());
        }
    }

    @Test
    void isPlausibleReading_shouldRejectKanaSequencesJapaneseDoesNotHave() {
        assertThat(QuizDistractorGenerator.isPlausibleReading("がっこう")).isTrue();
        assertThat(QuizDistractorGenerator.isPlausibleReading("しゅっぱつ")).isTrue();
        assertThat(QuizDistractorGenerator.isPlausibleReading("み(る)")).isTrue();

        assertThat(QuizDistractorGenerator.isPlausibleReading("っこう")).isFalse();
        assertThat(QuizDistractorGenerator.isPlausibleReading("がっ")).isFalse();
        assertThat(QuizDistractorGenerator.isPlausibleReading("がっあ")).isFalse();
        assertThat(QuizDistractorGenerator.isPlausibleReading("かゃく")).isFalse();
        assertThat(QuizDistractorGenerator.isPlausibleReading("いゃく")).isFalse();
        assertThat(QuizDistractorGenerator.isPlausibleReading("んか")).isFalse();
    }

    private static int differingChars(String a, String b) {
        int[] x = a.codePoints().toArray();
        int[] y = b.codePoints().toArray();
        return (int) IntStream.range(0, x.length).filter(i -> x[i] != y[i]).count();
    }
}
