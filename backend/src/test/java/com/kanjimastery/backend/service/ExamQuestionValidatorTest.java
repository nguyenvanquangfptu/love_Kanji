package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamSection;
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

    @Test
    void passageProblems_shouldRejectQuestionsOfTheOtherSection_soTheExamCannotTakeTheWrongPassage() {
        // Đoạn văn Ngữ pháp có câu Đọc hiểu, và bài đọc có câu 文章の文法.
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.GRAMMAR, "【1】",
                List.of(question(JlptQuestionType.READING_SHORT, 1)), null))
                .containsExactly("câu 1 là dạng READING_SHORT, không thuộc đoạn văn phần Ngữ pháp");
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING, "本文",
                List.of(question(JlptQuestionType.TEXT_GRAMMAR, 1)), 1))
                .containsExactly("câu 1 là dạng TEXT_GRAMMAR, không thuộc đoạn văn phần Đọc hiểu");
        // Câu đứng riêng của phần Ngữ pháp cũng không thuộc đoạn văn nào.
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.GRAMMAR, "【1】",
                List.of(question(JlptQuestionType.GRAMMAR_FORM, 1)), null))
                .containsExactly("câu 1 là dạng GRAMMAR_FORM, không thuộc đoạn văn phần Ngữ pháp");
        assertThat(ExamQuestionValidator.passageProblems(null, "本文", List.of(), null))
                .containsExactly("đoạn văn chưa ghi thuộc phần thi nào");
    }

    @Test
    void passageProblems_shouldCheckTheBlanksOfAGrammarPassage_asBefore() {
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.GRAMMAR, "【1】と【2】",
                List.of(question(JlptQuestionType.TEXT_GRAMMAR, 1), question(JlptQuestionType.TEXT_GRAMMAR, 2)), null))
                .isEmpty();
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.GRAMMAR, "【1】",
                List.of(question(JlptQuestionType.TEXT_GRAMMAR, 1), question(JlptQuestionType.TEXT_GRAMMAR, 2)), null))
                .containsExactly("【2】 phải có đúng một lần trong đoạn văn");
    }

    @Test
    void passageProblems_shouldAcceptAWellFormedReadingPassage() {
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING,
                "先週、〔下線〕新しい店〔/下線〕に行った。\nとても静かだった。",
                List.of(question(JlptQuestionType.READING_MID, 2), question(JlptQuestionType.READING_MID, 1),
                        question(JlptQuestionType.READING_MID, 3)), 3)).isEmpty();
        // 情報検索: có bảng, hoặc có mục liệt kê.
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING,
                "料金表\n| コース | 料金 |\n| 初級 | 3,000円 |",
                List.of(question(JlptQuestionType.INFO_SEARCH, 1), question(JlptQuestionType.INFO_SEARCH, 2)), 2))
                .isEmpty();
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING,
                "お知らせ\n・受付：9時から\n・場所：2階",
                List.of(question(JlptQuestionType.INFO_SEARCH, 1), question(JlptQuestionType.INFO_SEARCH, 2)), 2))
                .isEmpty();
    }

    @Test
    void passageProblems_shouldListWhatIsWrongWithAReadingPassage() {
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING, "〔下線〕始めだけ",
                List.of(question(JlptQuestionType.READING_MID, 1), question(JlptQuestionType.READING_LONG, 3)), 3))
                .containsExactly(
                        "các câu hỏi của một bài đọc phải cùng một dạng, đang có [READING_MID, READING_LONG]",
                        "thứ tự các câu hỏi phải là 1..2",
                        "bài đọc dạng READING_MID cần 3 câu hỏi, đang có 2",
                        "bài đọc: thiếu 〔/下線〕 sau 〔下線〕");
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING, "〔/下線〕逆〔下線〕",
                List.of(question(JlptQuestionType.READING_SHORT, 1)), 1))
                .containsExactly("bài đọc: 〔下線〕 và 〔/下線〕 phải đi thành cặp, không lồng nhau");
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING, "ただの文章です。",
                List.of(question(JlptQuestionType.INFO_SEARCH, 1), question(JlptQuestionType.INFO_SEARCH, 2)), 2))
                .containsExactly("bài 情報検索 cần có bảng (các dòng | ô | ô |) hoặc mục liệt kê (dòng bắt đầu bằng ・ ■ ● ※...)");
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING, " ",
                List.of(question(JlptQuestionType.READING_SHORT, 1)), 1)).containsExactly("thiếu nội dung bài đọc");
        assertThat(ExamQuestionValidator.passageProblems(ExamSection.READING, "本文", List.of(), 1))
                .containsExactly("bài đọc chưa có câu hỏi");
    }

    @Test
    void problems_shouldCheckTheUnderlineOfAReadingQuestion() {
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.READING_LONG,
                "〔下線〕それ〔/下線〕とは何か。", null, null, OPTIONS, "A")).isEmpty();
        assertThat(ExamQuestionValidator.problems(JlptQuestionType.READING_LONG,
                "〔下線〕それとは何か。", null, null, OPTIONS, "A"))
                .containsExactly("câu hỏi: thiếu 〔/下線〕 sau 〔下線〕");
    }

    private static ExamQuestionValidator.PassageQuestion question(JlptQuestionType type, int number) {
        return new ExamQuestionValidator.PassageQuestion(type, number);
    }
}
