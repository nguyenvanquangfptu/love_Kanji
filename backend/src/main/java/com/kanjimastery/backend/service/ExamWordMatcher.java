package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tìm từ trong kho cho câu thi từ vựng - dùng chung cho lúc nhập đề ({@link ExamImportService}) và lúc gắn lại các câu
 * đã nhập khi kho có thêm từ ({@link ExamWordLinkService}).
 */
final class ExamWordMatcher {

    /** Đuôi okurigana của cách viết: 許す -> 許 (cột character của động từ chỉ có phần chữ Hán). */
    private static final Pattern TRAILING_HIRAGANA = Pattern.compile("\\p{IsHiragana}+$");
    /** Từ được hỏi cách dùng nằm trong đề bài câu 用法: 「Chọn câu dùng từ 「X」 đúng nhất.」 */
    private static final Pattern USAGE_KEYWORD = Pattern.compile("「(.+?)」");
    /** Từ viết toàn bằng kana (やさしい, ゆずる) - có thể là từ kho viết bằng chữ Hán. */
    private static final Pattern KANA = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}ー]+");

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

    /** Kho từ đã tìm sẵn cho một lô câu: theo cột character, và theo cách đọc (bỏ ngoặc) cho từ viết bằng kana. */
    record Bank(Map<String, List<Kanji>> byCharacter, Map<String, List<Kanji>> byReading) {

        static final Bank EMPTY = new Bank(Map.of(), Map.of());
    }

    /**
     * Kết quả tìm từ: các từ gắn với câu, và {@code inferred} khi phải suy ra (dạng từ điển của 怒って, hay cách đọc của
     * やさしい) chứ không khớp thẳng cách viết - để người duyệt xem lại trước khi ghi.
     */
    record Match(List<Kanji> words, boolean inferred) {

        static final Match NONE = new Match(List.of(), false);

        boolean found() {
            return !words.isEmpty();
        }
    }

    /** Tìm sẵn trong kho mọi cách viết / cách đọc có thể cần cho {@code hints}, mỗi loại một truy vấn. */
    static Bank bank(Collection<Hint> hints, KanjiRepository kanjiRepository) {
        Set<String> keys = new HashSet<>();
        Set<String> readings = new HashSet<>();
        for (Hint hint : hints) {
            for (String spelling : spellings(hint)) {
                keys.add(spelling);
                keys.add(stem(spelling));
                if (KANA.matcher(spelling).matches()) {
                    readings.add(spelling);
                }
            }
        }
        keys.remove("");
        if (keys.isEmpty()) {
            return Bank.EMPTY;
        }
        Map<String, List<Kanji>> byCharacter = kanjiRepository.findAllByCharacterIn(keys).stream()
                .collect(Collectors.groupingBy(Kanji::getCharacter));
        Map<String, List<Kanji>> byReading = readings.isEmpty() ? Map.of()
                : kanjiRepository.findAllByDisplayReadingIn(readings).stream()
                        .filter(kanji -> StringUtils.hasText(kanji.getReading()))
                        .collect(Collectors.groupingBy(kanji -> ReadingMatcher.displayReading(kanji.getReading())));
        return new Bank(byCharacter, byReading);
    }

    /** Cách viết của câu và các dạng từ điển suy ra từ nó. */
    private static List<String> spellings(Hint hint) {
        List<String> spellings = new ArrayList<>();
        spellings.add(hint.written());
        DictionaryForms.of(hint.written()).forEach(form -> spellings.addAll(form.spellings()));
        return spellings;
    }

    /**
     * Từ trong kho cho {@code hint}, theo thứ tự:
     * <ol>
     *   <li>khớp thẳng cách viết (như trước nay; kho có nhiều từ cùng chữ thì giữ từ có cách đọc khớp, nếu biết);</li>
     *   <li>câu viết bằng kana mà kho viết bằng chữ Hán (やさしい - 優しい): theo cách đọc;</li>
     *   <li>dạng từ điển suy ra ({@link DictionaryForms}, từng tầng): 怒って - 怒る, 連絡 - 連絡する, 楽だ - 楽.</li>
     * </ol>
     * Chỉ đoán khi chắc: một cách đọc ứng với nhiều từ khác nhau (こい: 濃い, 恋), hay một tầng có nhiều dạng đều có
     * trong kho (行って: 行う, 行く), thì không gắn gì.
     */
    static Match match(Hint hint, Bank bank) {
        List<Kanji> direct = direct(hint, bank.byCharacter());
        if (!direct.isEmpty()) {
            return new Match(direct, false);
        }
        Lookup kana = byReading(hint.written(), bank);
        if (kana.ambiguous()) {
            return Match.NONE;
        }
        if (!kana.words().isEmpty()) {
            return new Match(kana.words(), true);
        }
        List<DictionaryForms.Form> forms = DictionaryForms.of(hint.written());
        for (DictionaryForms.Tier tier : DictionaryForms.Tier.values()) {
            List<List<Kanji>> found = new ArrayList<>();
            for (DictionaryForms.Form form : forms) {
                if (form.tier() != tier) {
                    continue;
                }
                Lookup lookup = lookup(form, bank);
                if (lookup.ambiguous()) {
                    return Match.NONE;
                }
                if (!lookup.words().isEmpty()) {
                    found.add(lookup.words());
                }
            }
            if (found.size() == 1) {
                return new Match(found.get(0), true);
            }
            if (found.size() > 1) {
                return Match.NONE;
            }
        }
        return Match.NONE;
    }

    /** Kết quả tìm một dạng: các từ tìm thấy, hoặc nhập nhằng (nhiều từ khác nhau cùng cách đọc). */
    private record Lookup(List<Kanji> words, boolean ambiguous) {

        static final Lookup NOTHING = new Lookup(List.of(), false);
        static final Lookup AMBIGUOUS = new Lookup(List.of(), true);
    }

    /** Cách khớp có từ trước: cách viết đầy đủ, hoặc phần chữ Hán + okurigana trong cách đọc (許 + ゆる(す) = 許す). */
    private static List<Kanji> direct(Hint hint, Map<String, List<Kanji>> byCharacter) {
        Set<Kanji> candidates = new HashSet<>(byCharacter.getOrDefault(hint.written(), List.of()));
        byCharacter.getOrDefault(stem(hint.written()), List.of()).stream()
                .filter(kanji -> hint.written().equals(writtenForm(kanji)))
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

    /**
     * Các từ có đúng cách viết của một dạng suy ra. Phải khớp cả okurigana: danh từ 話 không được nhận cho 話す, chữ 見
     * (み(る)) chỉ khớp 見る. Không có thì thử theo cách đọc nếu dạng đó viết bằng kana.
     */
    private static Lookup lookup(DictionaryForms.Form form, Bank bank) {
        Set<Kanji> words = new HashSet<>();
        for (String spelling : form.spellings()) {
            for (String key : new LinkedHashSet<>(List.of(spelling, stem(spelling)))) {
                bank.byCharacter().getOrDefault(key, List.of()).stream()
                        .filter(kanji -> spelling.equals(writtenForm(kanji)))
                        .forEach(words::add);
            }
        }
        if (!words.isEmpty()) {
            return new Lookup(List.copyOf(words), false);
        }
        for (String spelling : form.spellings()) {
            Lookup kana = byReading(spelling, bank);
            if (kana.ambiguous() || !kana.words().isEmpty()) {
                return kana;
            }
        }
        return Lookup.NOTHING;
    }

    /** Từ viết bằng chữ Hán có cách đọc là {@code kana}; nhiều từ khác cách viết cùng cách đọc thì là nhập nhằng. */
    private static Lookup byReading(String kana, Bank bank) {
        if (!KANA.matcher(kana).matches()) {
            return Lookup.NOTHING;
        }
        List<Kanji> words = bank.byReading().getOrDefault(kana, List.of());
        long spellings = words.stream().map(ExamWordMatcher::writtenForm).distinct().count();
        if (spellings > 1) {
            return Lookup.AMBIGUOUS;
        }
        return words.isEmpty() ? Lookup.NOTHING : new Lookup(words, false);
    }

    /** Các từ của {@code match} để báo: 「怒る」, 「表 (おもて)」... */
    static String describe(Match match) {
        return match.words().stream()
                .map(kanji -> "「" + writtenForm(kanji) + "」")
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String writtenForm(Kanji kanji) {
        return ReadingMatcher.writtenForm(kanji.getCharacter(), kanji.getReading());
    }

    private static String stem(String written) {
        return TRAILING_HIRAGANA.matcher(written).replaceFirst("");
    }
}
