package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.RubySegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FuriganaServiceTest {

    // Nạp từ điển Kuromoji một lần cho cả lớp test.
    private static final FuriganaService furigana = new FuriganaService();

    @Test
    void annotate_shouldPutTheReadingOnlyOverKanji_leavingOkuriganaAndKanaPlain() {
        List<RubySegment> segments = furigana.annotate("毎朝パンを食べます。");

        assertThat(segments).containsExactly(
                new RubySegment("毎朝", "まいあさ", false),
                new RubySegment("パンを", null, false),
                new RubySegment("食", "た", false),
                new RubySegment("べます。", null, false));
    }

    @Test
    void annotate_shouldKeepTheSentenceIntact_withBlanksAndFullWidthDigits() {
        String sentence = "３時に（　　）、駅で＿★＿会いましょう。";

        List<RubySegment> segments = furigana.annotate(sentence);

        assertThat(text(segments)).isEqualTo(sentence);
        assertThat(segments).contains(new RubySegment("時", "じ", false), new RubySegment("駅", "えき", false),
                new RubySegment("会", "あ", false));
        assertThat(segments).filteredOn(segment -> segment.text().contains("（　　）")).singleElement()
                .extracting(RubySegment::ruby).isNull();
    }

    @Test
    void annotate_shouldReadTheWordAsked_fromTheWordBank_notAsKuromojiGuesses() {
        assertThat(furigana.annotate("今日は寒い。").get(0)).isEqualTo(new RubySegment("今日", "きょう", false));

        List<RubySegment> segments = furigana.annotate("今日の社会は便利だ。", "今日", "こんにち", false);

        assertThat(segments.get(0)).isEqualTo(new RubySegment("今日", "こんにち", true));
        assertThat(segments).contains(new RubySegment("社会", "しゃかい", false));
    }

    @Test
    void annotate_shouldNotSendTheReadingOfTheWordAsked_atAnyOfItsPlaces_whenItsReadingIsTheQuestion() {
        String sentence = "本を読む人は本が好きだ。";

        List<RubySegment> segments = furigana.annotate(sentence, "本", "ほん", true);

        assertThat(text(segments)).isEqualTo(sentence);
        // Lần thứ hai không gạch chân và không có furigana nên gộp vào đoạn chữ thường bên cạnh.
        assertThat(segments).filteredOn(segment -> segment.text().contains("本"))
                .extracting(RubySegment::text, RubySegment::ruby, RubySegment::target)
                .containsExactly(tuple("本", null, true), tuple("は本が", null, false));
        assertThat(segments).extracting(RubySegment::ruby).doesNotContain("ほん");
        assertThat(segments).contains(new RubySegment("人", "ひと", false));
    }

    @Test
    void annotate_shouldUnderlineOnlyTheFirstPlaceOfTheWordAsked() {
        List<RubySegment> segments = furigana.annotate("この肉は固い。", "固い", "かたい", false);

        assertThat(segments).containsExactly(
                new RubySegment("この", null, false),
                new RubySegment("肉", "にく", false),
                new RubySegment("は", null, false),
                new RubySegment("固", "かた", true),
                new RubySegment("い", null, true),
                new RubySegment("。", null, false));
    }

    @Test
    void annotate_shouldUseTheKanjiPartOfAReadingWithOkuriganaInBrackets() {
        assertThat(FuriganaService.kanaReading("見", "み(る)")).isEqualTo("み");
        assertThat(FuriganaService.kanaReading("見る", "み(る)")).isEqualTo("みる");
        assertThat(FuriganaService.kanaReading("固い", "かたい")).isEqualTo("かたい");

        assertThat(furigana.annotate("テレビを見ます。", "見", "み(る)", false))
                .contains(new RubySegment("見", "み", true));
    }

    @Test
    void annotate_shouldLeaveKanaOnlySentencesWithoutFurigana_andNoSentenceAsNull() {
        assertThat(furigana.annotate("テレビをみる。")).containsExactly(new RubySegment("テレビをみる。", null, false));
        assertThat(furigana.annotate(null)).isNull();
        assertThat(furigana.annotate("  ")).isNull();
    }

    @Test
    void annotate_shouldReadNihonAsTextbooksDo_notNippon() {
        assertThat(furigana.annotate("日本語を勉強します。")).startsWith(new RubySegment("日本語", "にほんご", false));
        assertThat(furigana.annotate("日本に行きます。")).startsWith(new RubySegment("日本", "にほん", false));
    }

    @Test
    void annotate_shouldReadTheIterationMarkWithTheKanjiBeforeIt() {
        assertThat(furigana.annotate("人々が集まる。")).startsWith(new RubySegment("人々", "ひとびと", false));
    }

    @Test
    void align_shouldSplitTheReadingBetweenKanjiRuns_aroundTheKanaBetweenThem() {
        assertThat(FuriganaService.align("取り消す", "とりけす")).containsExactly(
                new RubySegment("取", "と", false),
                new RubySegment("り", null, false),
                new RubySegment("消", "け", false),
                new RubySegment("す", null, false));
    }

    @Test
    void align_shouldPutTheWholeReadingOverTheWord_whenItDoesNotFitTheKana() {
        assertThat(FuriganaService.align("食べる", "のむ")).containsExactly(new RubySegment("食べる", "のむ", false));
        assertThat(FuriganaService.align("食べる", null)).containsExactly(new RubySegment("食べる", null, false));
    }

    @Test
    void align_shouldCountKanjiOutsideTheBasicPlane_asOneCharacter() {
        // 𠮟 (U+20B9F) là hai char trong Java.
        assertThat(FuriganaService.align("𠮟る", "しかる")).containsExactly(
                new RubySegment("𠮟", "しか", false),
                new RubySegment("る", null, false));
    }

    private static String text(List<RubySegment> segments) {
        return segments.stream().map(RubySegment::text).collect(Collectors.joining());
    }
}
