package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Kiểm tra cấu trúc một câu thi theo dạng câu JLPT: đủ 4 lựa chọn khác nhau, đáp án A-D, câu có đúng chỗ được hỏi
 * (phần gạch chân, ô （　　）, các ô của câu sắp xếp, các chỗ trống 【n】 của đoạn văn), đoạn văn có câu hỏi đúng phần
 * thi của nó. Dùng khi sửa, duyệt câu, khi nhập đề và khi nhận câu nháp từ AI.
 */
final class ExamQuestionValidator {

    static final String ORDER_SLOT = "＿＿＿";
    static final String ORDER_STAR = "＿★＿";
    /** Cột option_a..option_d là VARCHAR(255), highlight là VARCHAR(100). */
    static final int MAX_OPTION_LENGTH = 255;
    static final int MAX_HIGHLIGHT_LENGTH = 100;
    /** Chỗ trống 【n】 trong đoạn văn 文章の文法; câu hỏi điền hai chỗ (a) và (b) thì đánh 【na】 và 【nb】. */
    static final Pattern PASSAGE_BLANK = Pattern.compile("【(\\d+)([ab]?)】");
    /** Phần gạch chân trong bài đọc và câu hỏi Đọc hiểu: 〔下線〕...〔/下線〕. */
    static final String UNDERLINE_OPEN = "〔下線〕";
    static final String UNDERLINE_CLOSE = "〔/下線〕";
    private static final Pattern UNDERLINE_MARK = Pattern.compile("〔(/?)下線〕");
    /** Một dòng của bảng trong bài 情報検索: | ô | ô |. */
    private static final Pattern TABLE_ROW = Pattern.compile("(?m)^[ \\t　]*\\|.*\\|[ \\t　]*$");
    /** Một mục liệt kê trong thông báo, tờ rơi của bài 情報検索. */
    private static final Pattern LIST_ITEM = Pattern.compile("(?m)^[ \\t　]*[・■□●○◆◇※]");

    /** Một câu hỏi của đoạn văn: dạng câu và số thứ tự (chỗ trống 【n】, hoặc thứ tự câu trong bài đọc). */
    record PassageQuestion(JlptQuestionType type, Integer number) {
    }

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
            case JlptQuestionType.READING_SHORT, JlptQuestionType.READING_MID, JlptQuestionType.READING_LONG,
                 JlptQuestionType.INFO_SEARCH -> underlineProblems(questionText).forEach(problem ->
                    problems.add("câu hỏi: " + problem));
            default -> {
                // 用法: bốn lựa chọn là bốn câu, không cần câu dẫn; 文章の文法: chỗ trống nằm trong đoạn văn
                // (xem passageProblems).
            }
        }
        return problems;
    }

    /**
     * Lỗi của một đoạn văn theo phần thi của nó; {@code perPassage} là số câu mỗi bài theo cấu trúc đề (null = không
     * kiểm tra). Mọi câu hỏi phải là dạng của đúng phần thi đó - đoạn văn Ngữ pháp có câu Đọc hiểu (hay ngược lại) thì
     * ghép đề sẽ lấy nhầm bài. Phần Ngữ pháp: các chỗ trống 【n】 như {@link #passageProblems(String, List)}. Phần Đọc
     * hiểu: các câu cùng một dạng, đánh số 1..n, đủ số câu mỗi bài, 〔下線〕 đủ cặp; bài 情報検索 có bảng hoặc mục liệt kê.
     */
    static List<String> passageProblems(ExamSection section, String content, List<PassageQuestion> questions,
                                        Integer perPassage) {
        List<String> problems = new ArrayList<>();
        if (section == null) {
            problems.add("đoạn văn chưa ghi thuộc phần thi nào");
            return problems;
        }
        questions.stream()
                .filter(question -> question.type() == null || question.type().section() != section
                        || !question.type().usesPassage())
                .forEach(question -> problems.add("câu " + question.number() + " là dạng " + question.type()
                        + ", không thuộc đoạn văn phần " + section.vietnameseName()));
        List<Integer> numbers = questions.stream().map(PassageQuestion::number).toList();
        if (section != ExamSection.READING) {
            problems.addAll(passageProblems(content, numbers));
            return problems;
        }
        if (!StringUtils.hasText(content)) {
            problems.add("thiếu nội dung bài đọc");
            return problems;
        }
        if (questions.isEmpty()) {
            problems.add("bài đọc chưa có câu hỏi");
            return problems;
        }
        Set<JlptQuestionType> types = questions.stream().map(PassageQuestion::type).filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(JlptQuestionType.class)));
        if (types.size() > 1) {
            problems.add("các câu hỏi của một bài đọc phải cùng một dạng, đang có " + types);
        }
        if (numbers.stream().anyMatch(Objects::isNull)
                || !numbers.stream().sorted().toList().equals(IntStream.rangeClosed(1, numbers.size()).boxed().toList())) {
            problems.add("thứ tự các câu hỏi phải là 1.." + numbers.size());
        }
        if (perPassage != null && questions.size() != perPassage) {
            problems.add("bài đọc dạng " + questions.get(0).type() + " cần " + perPassage + " câu hỏi, đang có "
                    + questions.size());
        }
        underlineProblems(content).forEach(problem -> problems.add("bài đọc: " + problem));
        if (types.contains(JlptQuestionType.INFO_SEARCH) && countMatches(TABLE_ROW, content) < 2
                && countMatches(LIST_ITEM, content) < 2) {
            problems.add("bài 情報検索 cần có bảng (các dòng | ô | ô |) hoặc mục liệt kê (dòng bắt đầu bằng ・ ■ ● ※...)");
        }
        return problems;
    }

    /** 〔下線〕 và 〔/下線〕 phải đi thành cặp, không lồng nhau. */
    private static List<String> underlineProblems(String text) {
        if (text == null) {
            return List.of();
        }
        Matcher matcher = UNDERLINE_MARK.matcher(text);
        boolean open = false;
        while (matcher.find()) {
            boolean closing = !matcher.group(1).isEmpty();
            if (closing != open) {
                return List.of(UNDERLINE_OPEN + " và " + UNDERLINE_CLOSE + " phải đi thành cặp, không lồng nhau");
            }
            open = !open;
        }
        return open ? List.of("thiếu " + UNDERLINE_CLOSE + " sau " + UNDERLINE_OPEN) : List.of();
    }

    private static int countMatches(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }

    /**
     * Lỗi của một đoạn văn 文章の文法 có các câu hỏi điền vào chỗ trống {@code blankNos}: chỗ trống phải là 1..n theo
     * thứ tự, mỗi 【k】 xuất hiện đúng một lần trong đoạn (hoặc đúng một cặp 【ka】【kb】 với câu điền hai chỗ) và không
     * thừa chỗ trống nào.
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
        // Số chỗ trống -> các hậu tố gặp trong đoạn ("" với 【k】, "a"/"b" với 【ka】/【kb】).
        Map<Integer, List<String>> markers = new TreeMap<>();
        Matcher matcher = PASSAGE_BLANK.matcher(content);
        while (matcher.find()) {
            markers.computeIfAbsent(Integer.parseInt(matcher.group(1)), blank -> new ArrayList<>()).add(matcher.group(2));
        }
        for (int blank : expected) {
            List<String> found = markers.getOrDefault(blank, List.of());
            boolean pairUsed = found.contains("a") || found.contains("b");
            if (!pairUsed && found.size() != 1) {
                problems.add("【" + blank + "】 phải có đúng một lần trong đoạn văn");
            } else if (pairUsed && !(found.size() == 2 && found.containsAll(List.of("a", "b")))) {
                problems.add("【" + blank + "a】 và 【" + blank + "b】 phải có mỗi chỗ đúng một lần trong đoạn văn");
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
