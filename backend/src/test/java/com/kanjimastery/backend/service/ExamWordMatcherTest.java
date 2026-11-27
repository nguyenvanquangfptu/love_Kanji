package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Suy lại từ được hỏi của câu đã có trong kho, và tìm từ đó trong kho như lúc nhập đề. */
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
        Kanji forgive = Kanji.builder().id(1L).character("許").reading("ゆる(す)").build();
        Kanji surface = Kanji.builder().id(2L).character("表").reading("ひょう").build();
        Kanji front = Kanji.builder().id(3L).character("表").reading("おもて").build();
        ExamWordMatcher.Hint verb = new ExamWordMatcher.Hint("許す", null);
        ExamWordMatcher.Hint omote = new ExamWordMatcher.Hint("表", "おもて");
        ExamWordMatcher.Hint unknownReading = new ExamWordMatcher.Hint("表", "あらわ");

        assertThat(ExamWordMatcher.keys(List.of(verb, omote))).containsExactlyInAnyOrder("許す", "許", "表");
        Map<String, List<Kanji>> byCharacter = List.of(forgive, surface, front).stream()
                .collect(Collectors.groupingBy(Kanji::getCharacter));

        assertThat(ExamWordMatcher.match(verb, byCharacter)).containsExactly(forgive);
        assertThat(ExamWordMatcher.match(omote, byCharacter)).containsExactly(front);
        assertThat(ExamWordMatcher.match(unknownReading, byCharacter)).as("không khớp cách đọc nào thì giữ cả hai")
                .containsExactlyInAnyOrder(surface, front);
        assertThat(ExamWordMatcher.match(new ExamWordMatcher.Hint("許して", null), byCharacter))
                .as("dạng đã chia không khớp dạng từ điển").isEmpty();
    }

    private static ExamQuestion question(JlptQuestionType type, String highlight, String correct) {
        return ExamQuestion.builder().questionType(type).questionText("Đề bài").highlight(highlight)
                .optionA("x").optionB(correct).optionC("y").optionD("z").correctOption("B").build();
    }
}
