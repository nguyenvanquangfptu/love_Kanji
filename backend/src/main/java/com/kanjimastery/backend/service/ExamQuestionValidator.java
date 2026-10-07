package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptQuestionType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Kiểm tra cấu trúc một câu thi theo dạng câu JLPT: đủ 4 lựa chọn khác nhau, đáp án A-D, câu có đúng chỗ được hỏi
 * (phần gạch chân, ô （　　）, các ô của câu sắp xếp, các chỗ trống 【n】 của đoạn văn). Dùng khi sửa, duyệt câu và
 * khi nhận câu nháp từ AI.
 */
final class ExamQuestionValidator {

    static final String ORDER_SLOT = "＿＿＿";
    static final String ORDER_STAR = "＿★＿";
    /** Cột option_a..option_d là VARCHAR(255), highlight là VARCHAR(100). */
    static final int MAX_OPTION_LENGTH = 255;
    static final int MAX_HIGHLIGHT_LENGTH = 100;
    /** Chỗ trống 【n】 trong đoạn văn 文章の文法. */
    static final Pattern PASSAGE_BLANK = Pattern.compile("【(\\d+)】");

    private ExamQuestionValidator() {
    }

    /** Các lỗi tìm thấy; rỗng = hợp lệ. */
    static List<String> problems(JlptQuestionType questionType, String questionText, String sentence, String highlight,
                                 List<String> options, String correctOption) {
        List<String> problems = new ArrayList<>();
        if (!StringUtils.hasText(questionText)) {
            problems.add("thiếu nội dung câu hỏi");
        }
        if (options.size() != 4 || options.stream().anyMatch(option -> !StringUtils.hasText(option))) {
            problems.add("cần đủ 4 lựa chọn");
        } else {
            Set<String> distinct = new HashSet<>();
            options.forEach(option -> distinct.add(option.strip()));
            if (distinct.size() < 4) {
                problems.add("có lựa chọn trùng nhau");
            }
            if (options.stream().anyMatch(option -> option.length() > MAX_OPTION_LENGTH)) {
                problems.add("lựa chọn dài quá " + MAX_OPTION_LENGTH + " ký tự");
            }
        }
        if (correctOption == null || !correctOption.matches("[A-D]")) {
            problems.add("đáp án phải là A, B, C hoặc D");
        }
        if (questionType == null) {
            return problems;
        }
        String text = sentence == null ? "" : sentence;
        switch (questionType) {
            case JlptQuestionType.KANJI_READING, JlptQuestionType.ORTHOGRAPHY, JlptQuestionType.PARAPHRASE -> {
                if (!StringUtils.hasText(highlight) || !text.contains(highlight)) {
                    problems.add("câu phải chứa phần được gạch chân");
                }
            }
            case JlptQuestionType.CONTEXT, JlptQuestionType.GRAMMAR_FORM -> {
                if (count(text, ExamQuestionGenerator.BLANK) != 1) {
                    problems.add("câu phải có đúng một ô " + ExamQuestionGenerator.BLANK);
                }
            }
            case JlptQuestionType.SENTENCE_ORDER -> {
                if (count(text, ORDER_STAR) != 1 || count(text, ORDER_SLOT) != 3) {
                    problems.add("câu sắp xếp phải có 3 ô " + ORDER_SLOT + " và 1 ô " + ORDER_STAR);
                }
            }
            default -> {
                // 用法: bốn lựa chọn là bốn câu, không cần câu dẫn; 文章の文法: chỗ trống nằm trong đoạn văn
                // (xem passageProblems).
            }
        }
        return problems;
    }

    /**
     * Lỗi của một đoạn văn 文章の文法 có các câu hỏi điền vào chỗ trống {@code blankNos}: chỗ trống phải là 1..n theo
     * thứ tự, mỗi 【k】 xuất hiện đúng một lần trong đoạn và không thừa chỗ trống nào.
     */
    static List<String> passageProblems(String content, List<Integer> blankNos) {
        List<String> problems = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            problems.add("thiếu nội dung đoạn văn");
            return problems;
        }
        if (blankNos.isEmpty()) {
            problems.add("đoạn văn chưa có câu hỏi");
        }
        List<Integer> expected = IntStream.rangeClosed(1, blankNos.size()).boxed().toList();
        if (!blankNos.stream().sorted().toList().equals(expected)) {
            problems.add("chỗ trống của các câu hỏi phải là 1.." + blankNos.size());
        }
        Map<Integer, Integer> markers = new TreeMap<>();
        Matcher matcher = PASSAGE_BLANK.matcher(content);
        while (matcher.find()) {
            markers.merge(Integer.parseInt(matcher.group(1)), 1, Integer::sum);
        }
        for (int blank : expected) {
            if (markers.getOrDefault(blank, 0) != 1) {
                problems.add("【" + blank + "】 phải có đúng một lần trong đoạn văn");
            }
        }
        markers.keySet().stream().filter(blank -> !expected.contains(blank))
                .forEach(blank -> problems.add("thừa chỗ trống 【" + blank + "】"));
        return problems;
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
            count++;
        }
        return count;
    }
}
