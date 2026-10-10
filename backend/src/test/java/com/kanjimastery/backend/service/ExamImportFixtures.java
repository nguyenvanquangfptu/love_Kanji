package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.ExamImportRequest;
import com.kanjimastery.backend.dto.ExamImportRequest.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Một đề N3 đủ 58 câu đúng cấu trúc (Từ vựng 8-6-11-5-5, Ngữ pháp 13-5-5) để thử nhập đề. Nội dung chỉ là chữ giữ
 * chỗ, mỗi câu khác nhau theo số câu; đáp án đúng luôn là lựa chọn 1, riêng câu sắp xếp là lựa chọn ở ô ★.
 */
public final class ExamImportFixtures {

    private ExamImportFixtures() {
    }

    public static ExamImportRequest n3Test(String code) {
        return new ExamImportRequest(code, "N3", vocabulary(), grammar(passage("【19】、【20】、【21】、【22】、【23】。")));
    }

    public static ExamImportRequest.VocabularyPart vocabulary() {
        return new ExamImportRequest.VocabularyPart(
                items(1, 8, ExamImportFixtures::underlined),
                items(9, 14, ExamImportFixtures::underlined),
                items(15, 25, ExamImportFixtures::blank),
                items(26, 30, ExamImportFixtures::underlined),
                items(31, 35, ExamImportFixtures::usage));
    }

    public static ExamImportRequest.GrammarPart grammar(ExamImportRequest.Passage passage) {
        return new ExamImportRequest.GrammarPart(
                items(1, 13, ExamImportFixtures::blank),
                items(14, 18, ExamImportFixtures::order),
                passage);
    }

    public static ExamImportRequest.Passage passage(String content) {
        return new ExamImportRequest.Passage("商店街の名前", content, items(19, 23, ExamImportFixtures::passageQuestion));
    }

    public static List<Item> items(int from, int to, IntFunction<Item> item) {
        return new ArrayList<>(IntStream.rangeClosed(from, to).mapToObj(item).toList());
    }

    /** Câu có phần gạch chân (漢字読み, 表記, 言い換え). */
    public static Item underlined(int number) {
        return item(number, "これは語" + number + "です。", "語" + number, null, options(number), 1, null, null);
    }

    /** Câu điền vào ô trống, ô viết lệch như khi chép tay. */
    public static Item blank(int number) {
        return item(number, "これは（  ）" + number + "です。", null, null, options(number), 1, null, null);
    }

    public static Item usage(int number) {
        return new Item(number, null, null, "語" + number, null, null,
                List.of("文" + number + "あ", "文" + number + "い", "文" + number + "う", "文" + number + "え"),
                1, null, null, null, null, null);
    }

    /** Câu sắp xếp: thứ tự đúng 2-4-3-1, ★ ở ô 3 nên đáp án là lựa chọn 3. */
    public static Item order(int number) {
        return new Item(number, null, null, null, "今日は", "。", options(number), 3, List.of(2, 4, 3, 1), 3,
                null, null, null);
    }

    public static Item passageQuestion(int number) {
        return item(number, null, null, null, options(number), 1, null, null);
    }

    public static Item item(int number, String sentence, String highlight, String keyword, List<String> options,
                            Integer answer, String word, String grammarPattern) {
        return new Item(number, sentence, highlight, keyword, null, null, options, answer, null, null, word,
                grammarPattern, null);
    }

    public static List<String> options(int number) {
        return List.of("一" + number, "二" + number, "三" + number, "四" + number);
    }
}
