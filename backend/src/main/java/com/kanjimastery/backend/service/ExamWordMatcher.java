package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.Kanji;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tìm từ trong kho cho câu thi từ vựng - dùng chung cho lúc nhập đề ({@link ExamImportService}) và lúc gắn lại các câu
 * đã nhập khi kho có thêm từ ({@link ExamWordLinkService}).
 */
final class ExamWordMatcher {

    /** Đuôi okurigana của cách viết: 許す -> 許 (cột character của động từ chỉ có phần chữ Hán). */
    private static final Pattern TRAILING_HIRAGANA = Pattern.compile("\\p{IsHiragana}+$");
    /** Từ được hỏi cách dùng nằm trong đề bài câu 用法: 「Chọn câu dùng từ 「X」 đúng nhất.」 */
    private static final Pattern USAGE_KEYWORD = Pattern.compile("「(.+?)」");

    private ExamWordMatcher() {
    }

    /** Từ câu hỏi kiểm tra: cách viết, và cách đọc nếu biết (để chọn đúng từ khi kho có nhiều từ cùng chữ). */
    record Hint(String written, String reading) {
    }

    /**
     * Từ của một câu đã có trong kho, suy lại như lúc nhập đề khi file không ghi "tu": phần gạch chân / đáp án đúng / từ
     * khoá. Null với câu không thuộc dạng từ vựng hoặc thiếu phần cần dùng.
     */
    static Hint hintOf(ExamQuestion question) {
        if (question.getQuestionType() == null) {
            return null;
        }
        String correct = question.getCorrectOption() == null ? null : question.optionText(question.getCorrectOption());
        return switch (question.getQuestionType()) {
            case KANJI_READING -> hint(question.getHighlight(), correct);
            case ORTHOGRAPHY -> hint(correct, question.getHighlight());
            case CONTEXT -> hint(correct, null);
            case PARAPHRASE -> hint(question.getHighlight(), null);
            case USAGE -> {
                Matcher keyword = USAGE_KEYWORD.matcher(question.getQuestionText() == null ? "" : question.getQuestionText());
                yield keyword.find() ? hint(keyword.group(1), null) : null;
            }
            default -> null;
        };
    }

    private static Hint hint(String written, String reading) {
        if (!StringUtils.hasText(written)) {
            return null;
        }
        return new Hint(written.strip(), StringUtils.hasText(reading) ? reading.strip() : null);
    }

    /** Các cách viết cần tìm trong kho cho {@code hints}: cách viết đầy đủ và phần chữ Hán (bỏ đuôi okurigana). */
    static Set<String> keys(Collection<Hint> hints) {
        Set<String> keys = new HashSet<>();
        for (Hint hint : hints) {
            keys.add(hint.written());
            keys.add(stem(hint.written()));
        }
        keys.remove("");
        return keys;
    }

    /**
     * Các từ trong kho khớp {@code hint}, trong {@code byCharacter} (từ theo cột character, đã tìm theo {@link #keys}).
     * Kho có nhiều từ cùng chữ thì chỉ giữ từ có cách đọc khớp, nếu biết cách đọc và có từ khớp.
     */
    static List<Kanji> match(Hint hint, Map<String, List<Kanji>> byCharacter) {
        Set<Kanji> candidates = new HashSet<>(byCharacter.getOrDefault(hint.written(), List.of()));
        byCharacter.getOrDefault(stem(hint.written()), List.of()).stream()
                .filter(kanji -> hint.written().equals(ReadingMatcher.writtenForm(kanji.getCharacter(), kanji.getReading())))
                .forEach(candidates::add);
        List<Kanji> matches = new ArrayList<>(candidates);
        if (hint.reading() != null) {
            List<Kanji> sameReading = matches.stream()
                    .filter(kanji -> kanji.getReading() != null
                            && hint.reading().equals(ReadingMatcher.displayReading(kanji.getReading())))
                    .toList();
            if (!sameReading.isEmpty()) {
                matches = sameReading;
            }
        }
        return matches;
    }

    private static String stem(String written) {
        return TRAILING_HIRAGANA.matcher(written).replaceFirst("");
    }
}
