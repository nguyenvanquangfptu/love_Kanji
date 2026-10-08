package com.kanjimastery.backend.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chấm cách đọc người học tự gõ (romaji hoặc kana) với cách đọc trong kho, và gọi tên lỗi hay gặp khi gõ sai. Gõ gần
 * đúng vẫn là sai: thiếu trường âm, thiếu っ hay sai âm đục là một từ khác (おばさん ≠ おばあさん).
 */
public final class ReadingMatcher {

    /** Lỗi gõ hay gặp, để gợi ý người học sửa. */
    public enum Mistake {
        /** Thiếu hoặc thừa trường âm: とうきょう ↔ ときょ. */
        LONG_VOWEL,
        /** Thiếu hoặc thừa âm ngắt っ: しゅっしん ↔ しゅしん. */
        SOKUON,
        /** Sai âm đục / bán đục: が ↔ か, ぱ ↔ は. */
        DAKUTEN,
        /** Nhầm âm nhỏ ゃゅょ với âm lớn: しゅ ↔ しゆ. */
        SMALL_KANA,
        /** Gõ n trước nguyên âm thành hàng n: はんい gõ "hani" ra はに - cần "han'i". */
        N_BEFORE_VOWEL
    }

    /**
     * @param typedKana cách server hiểu đầu vào (hiragana)
     * @param mistake   loại lỗi khi sai, null nếu đúng hoặc không gọi tên được
     */
    public record Result(boolean correct, String typedKana, Mistake mistake) {
    }

    private static final String VOICED = "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽゔ";
    private static final String UNVOICED = "かきくけこさしすせそたちつてとはひふへほはひふへほう";
    private static final String SMALL = "ぁぃぅぇぉゃゅょゎ";
    private static final String LARGE = "あいうえおやゆよわ";
    private static final Map<Character, Character> VOWEL = new HashMap<>();
    private static final Pattern OKURIGANA = Pattern.compile("[(（]([^)）]+)[)）]");

    static {
        String[] rows = {
                "aあかさたなはまやらわがざだばぱぁゃゎ",
                "iいきしちにひみりぎじぢびぴぃ",
                "uうくすつぬふむゆるぐずづぶぷぅゅゔ",
                "eえけせてねへめれげぜでべぺぇ",
                "oおこそとのほもよろをごぞどぼぽぉょ",
        };
        for (String row : rows) {
            for (int i = 1; i < row.length(); i++) {
                VOWEL.put(row.charAt(i), row.charAt(0));
            }
        }
    }

    private ReadingMatcher() {
    }

    /**
     * Chấm {@code typed} với mọi cách đọc chấp nhận được (các mục cùng chữ trong kho). Rỗng nếu đầu vào không đọc được
     * (chữ số, chữ cái không thuộc romaji...).
     */
    public static Optional<Result> match(String typed, Collection<String> readings) {
        List<String> candidates = RomajiConverter.toHiragana(typed);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<String> expected = readings.stream().map(ReadingMatcher::normalize).distinct().toList();
        List<String> sounds = expected.stream().map(ReadingMatcher::sound).toList();
        for (String candidate : candidates) {
            if (sounds.contains(sound(candidate))) {
                return Optional.of(new Result(true, candidate, null));
            }
        }
        String typedKana = candidates.get(0);
        return Optional.of(new Result(false, typedKana, mistake(typed, candidates, expected)));
    }

    /**
     * Cách phát âm: ぢ đọc như じ, づ như ず - Hepburn viết cả hai là "ji", "zu" nên người gõ romaji không phân biệt được
     * (つづく gõ "tsuzuku").
     */
    static String sound(String kana) {
        return kana.replace('ぢ', 'じ').replace('づ', 'ず');
    }

    /** Cách đọc để so: bỏ ngoặc okurigana, katakana thành hiragana (でんしメール → でんしめーる, み(る) → みる). */
    public static String normalize(String reading) {
        return RomajiConverter.katakanaToHiragana(displayReading(reading));
    }

    /** Cách đọc để hiện cho người học: bỏ ngoặc okurigana, giữ katakana (み(る) → みる). */
    public static String displayReading(String reading) {
        return reading.strip().replaceAll("[()（）]", "");
    }

    /** Cách viết để hỏi: chữ kèm đuôi okurigana trong ngoặc của cách đọc, nếu có (見 + み(る) → 見る). */
    public static String writtenForm(String character, String reading) {
        if (reading == null) {
            return character;
        }
        Matcher okurigana = OKURIGANA.matcher(reading);
        if (!okurigana.find() || character.endsWith(okurigana.group(1))) {
            return character;
        }
        return character + okurigana.group(1);
    }

    private static Mistake mistake(String typed, List<String> candidates, List<String> expected) {
        for (String candidate : candidates) {
            for (String target : expected) {
                if (sameAfter(candidate, target, ReadingMatcher::withoutLongVowels)) {
                    return Mistake.LONG_VOWEL;
                }
                if (sameAfter(candidate, target, kana -> kana.replace("っ", ""))) {
                    return Mistake.SOKUON;
                }
                if (sameAfter(candidate, target, kana -> map(kana, VOICED, UNVOICED))) {
                    return Mistake.DAKUTEN;
                }
                if (sameAfter(candidate, target, kana -> map(kana, SMALL, LARGE))) {
                    return Mistake.SMALL_KANA;
                }
            }
        }
        return nBeforeVowel(typed, expected) ? Mistake.N_BEFORE_VOWEL : null;
    }

    private static boolean sameAfter(String typed, String target, UnaryOperator<String> transform) {
        return transform.apply(typed).equals(transform.apply(target));
    }

    /** Gõ lại với một chữ n trước nguyên âm thành n' (hani → han'i) mà khớp thì lỗi là ん trước nguyên âm. */
    private static boolean nBeforeVowel(String typed, List<String> expected) {
        String text = typed.strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i + 1 < text.length(); i++) {
            boolean singleN = text.charAt(i) == 'n' && (i == 0 || text.charAt(i - 1) != 'n');
            if (singleN && "aiueoy".indexOf(text.charAt(i + 1)) >= 0) {
                String fixed = text.substring(0, i + 1) + "'" + text.substring(i + 1);
                if (RomajiConverter.toHiragana(fixed).stream().anyMatch(expected::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Bỏ trường âm: ー, và nguyên âm lặp lại nguyên âm trước (かあ, とう, せい, きい). */
    static String withoutLongVowels(String kana) {
        StringBuilder result = new StringBuilder();
        Character previous = null;
        for (char c : kana.toCharArray()) {
            if (c == 'ー') {
                continue;
            }
            Character vowel = VOWEL.get(c);
            boolean pureVowel = "あいうえお".indexOf(c) >= 0;
            boolean lengthens = pureVowel && previous != null
                    && (vowel.equals(previous) || c == 'う' && previous == 'o' || c == 'い' && previous == 'e');
            if (!lengthens) {
                result.append(c);
            }
            if (vowel != null) {
                previous = vowel;
            }
        }
        return result.toString();
    }

    private static String map(String kana, String from, String to) {
        StringBuilder result = new StringBuilder(kana.length());
        for (char c : kana.toCharArray()) {
            int at = from.indexOf(c);
            result.append(at >= 0 ? to.charAt(at) : c);
        }
        return result.toString();
    }
}
