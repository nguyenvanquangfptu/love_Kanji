package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptQuestionType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra cấu trúc một câu thi theo dạng câu JLPT: đủ 4 lựa chọn khác nhau, đáp án A-D, câu có đúng chỗ được hỏi
 * (phần gạch chân, ô （　　）, các ô của câu sắp xếp). Dùng khi sửa, duyệt câu và khi nhận câu nháp từ AI.
 */
final class ExamQuestionValidator {

    static final String ORDER_SLOT = "＿＿＿";
    static final String ORDER_STAR = "＿★＿";
    private static final int MAX_OPTION_LENGTH = 255;

    private ExamQuestionValidator() {
    }

    /** Các lỗi tìm thấy; rỗng = hợp lệ. */
    static List<String> problems(String questionType, String questionText, String sentence, String highlight,
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
            case JlptQuestionType.CONTEXT, JlptQuestionType.GRAMMAR_FORM, JlptQuestionType.TEXT_GRAMMAR -> {
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
                // 用法: bốn lựa chọn là bốn câu, không cần câu dẫn.
            }
        }
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
