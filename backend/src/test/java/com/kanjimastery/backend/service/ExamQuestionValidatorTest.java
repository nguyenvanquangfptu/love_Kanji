package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptQuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamQuestionValidatorTest {

    private static final List<String> OPTIONS = List.of("が", "を", "に", "で");

    @Test
    void problems_shouldAcceptWellFormedQuestionsOfEachType() {
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.GRAMMAR_FORM, "Chọn ngữ pháp",
                "パン（　　）食べます。", null, OPTIONS, "B")).isEmpty();
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.SENTENCE_ORDER, "Sắp xếp",
                "母が ＿＿＿ ＿＿＿ ＿★＿ ＿＿＿ おいしかった。", null, OPTIONS, "C")).isEmpty();
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.PARAPHRASE, "Gần nghĩa",
                "一人で行くのは不安だ。", "不安だ", OPTIONS, "A")).isEmpty();
        // 用法 không cần câu dẫn: bốn lựa chọn là bốn câu.
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.USAGE, "Chọn câu dùng 「預ける」 đúng nhất.",
                null, null, OPTIONS, "D")).isEmpty();
    }

    @Test
    void problems_shouldListWhatIsWrong() {
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.GRAMMAR_FORM, " ", "パンを食べます。", null,
                List.of("が", "が", "に", "で"), "E"))
                .containsExactly("thiếu nội dung câu hỏi", "có lựa chọn trùng nhau", "đáp án phải là A, B, C hoặc D",
                        "câu phải có đúng một ô （　　）");
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.SENTENCE_ORDER, "Sắp xếp",
                "母が ＿＿＿ ＿＿＿ ＿＿＿ ＿＿＿ おいしかった。", null, OPTIONS, "A"))
                .containsExactly("câu sắp xếp phải có 3 ô ＿＿＿ và 1 ô ＿★＿");
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.KANJI_READING, "Đọc", "毎朝新聞を読みます。",
                "雑誌", OPTIONS, "A")).containsExactly("câu phải chứa phần được gạch chân");
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.USAGE, "Câu", null, null,
                List.of("a", "b", "c"), "A")).containsExactly("cần đủ 4 lựa chọn");
    }

    @Test
    void passageProblems_shouldAcceptAPairOfBlanks_forAQuestionFillingTwoPlaces() {
        assertThat(ExamQuestionValidator.passageProblems("【1】、【2a】と【2b】。", List.of(1, 2))).isEmpty();
        assertThat(ExamQuestionValidator.passageProblems("【1】、【2a】と【2a】。", List.of(1, 2)))
                .containsExactly("【2a】 và 【2b】 phải có mỗi chỗ đúng một lần trong đoạn văn");
        assertThat(ExamQuestionValidator.passageProblems("【1】、【2】と【2b】。", List.of(1, 2)))
                .containsExactly("【2a】 và 【2b】 phải có mỗi chỗ đúng một lần trong đoạn văn");
    }

    @Test
    void passageProblems_shouldMatchTheBlanksOfTheTextWithTheQuestions() {
        String text = "今日は雨でした。【1】、学校へ行きました。友達と【2】話しました。";

        assertThat(ExamQuestionValidator.passageProblems(text, List.of(1, 2))).isEmpty();
        assertThat(ExamQuestionValidator.passageProblems(text, List.of(1, 3)))
                .containsExactly("chỗ trống của các câu hỏi phải là 1..2");
        assertThat(ExamQuestionValidator.passageProblems("【1】と【1】、それから【3】。", List.of(1)))
                .containsExactly("【1】 phải có đúng một lần trong đoạn văn", "thừa chỗ trống 【3】");
        assertThat(ExamQuestionValidator.passageProblems(" ", List.of(1))).containsExactly("thiếu nội dung đoạn văn");
        // Câu của đoạn văn không cần ô （　　） trong câu riêng: chỗ trống nằm trong đoạn.
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.TEXT_GRAMMAR, "【1】", null, null, OPTIONS, "A"))
                .isEmpty();
    }
}
