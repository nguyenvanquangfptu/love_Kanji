package com.kanjimastery.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Từ katakana cho 問題2 表記 của đề N5 - chọn cách viết katakana của từ viết bằng hiragana (てれび → テレビ): đổi sang
 * hiragana, và các cách viết sai dễ nhầm làm đáp án nhiễu. Mỗi cách viết sai chỉ khác từ đúng một chỗ, theo những lỗi
 * người học hay mắc: chữ giống hình (シ/ツ, ソ/ン, ル/レ...), thiếu hoặc thừa dấu ゛゜, trường âm ー, âm ngắt ッ, chữ nhỏ
 * ャュョァィェォッ viết thành chữ to. Từ đúng đọc lên khớp phần hiragana, các cách viết sai thì không.
 */
final class KatakanaSpelling {

    /** Các cặp chữ katakana giống hình. */
    private static final List<String> LOOK_ALIKE = List.of("シツ", "ソン", "ルレ", "クタ", "クワ", "ウワ", "ヌス", "ナメ",
            "チテ", "コユ", "アマ");
    private static final String PLAIN = "カキクケコサシスセソタチツテトハヒフヘホ";
    private static final String VOICED = "ガギグゲゴザジズゼゾダヂヅデドバビブベボ";
    private static final String H_ROW = "ハヒフヘホ";
    private static final String B_ROW = "バビブベボ";
    private static final String P_ROW = "パピプペポ";
    private static final String SMALL = "ャュョァィゥェォッ";
    private static final String LARGE = "ヤユヨアイウエオツ";
    /** Chữ đứng sau âm ngắt ッ được: hàng カ, サ, タ, パ. */
    private static final String AFTER_SOKUON = "カキクケコサシスセソタチツテトパピプペポ";
    private static final char LONG_VOWEL = 'ー';
    private static final char SOKUON = 'ッ';
    private static final char N = 'ン';

    private KatakanaSpelling() {
    }

    /** Một từ katakana (có thể có ー), ít nhất 2 chữ, không mở đầu bằng ー hay chữ nhỏ. */
    static boolean isKatakanaWord(String word) {
        if (word == null || word.length() < 2 || word.charAt(0) == LONG_VOWEL || SMALL.indexOf(word.charAt(0)) >= 0) {
            return false;
        }
        return word.chars().allMatch(c -> c == LONG_VOWEL || c >= 'ァ' && c <= 'ヶ');
    }

    /** テレビ → てれび, コーヒー → こーひー: giữ ー như đề thật. */
    static String toHiragana(String word) {
        StringBuilder text = new StringBuilder(word.length());
        for (char c : word.toCharArray()) {
            text.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - 0x60) : c);
        }
        return text.toString();
    }

    /** Từ có đúng một lần trong câu và không dính với chữ katakana khác (ペン trong ボールペン thì không). */
    static boolean standsAlone(String sentence, String word) {
        int at = sentence.indexOf(word);
        if (at < 0 || sentence.indexOf(word, at + word.length()) >= 0) {
            return false;
        }
        int end = at + word.length();
        return (at == 0 || !isKatakana(sentence.charAt(at - 1)))
                && (end == sentence.length() || !isKatakana(sentence.charAt(end)));
    }

    /**
     * {@code count} cách viết sai khác nhau; lấy lần lượt mỗi kiểu lỗi một cách để các đáp án nhiễu sai ở những chỗ khác
     * nhau. Ít hơn {@code count} nếu từ không đủ cách viết sai.
     */
    static List<String> misspellings(String word, int count) {
        List<List<String>> kinds = new ArrayList<>(kinds(word));
        kinds.forEach(Collections::shuffle);
        Collections.shuffle(kinds);
        Set<String> chosen = new LinkedHashSet<>();
        int longest = kinds.stream().mapToInt(List::size).max().orElse(0);
        for (int index = 0; index < longest && chosen.size() < count; index++) {
            for (List<String> kind : kinds) {
                if (index < kind.size() && chosen.size() < count) {
                    chosen.add(kind.get(index));
                }
            }
        }
        return List.copyOf(chosen);
    }

    /** Mọi cách viết sai một chỗ của từ. */
    static Set<String> allMisspellings(String word) {
        Set<String> all = new LinkedHashSet<>();
        kinds(word).forEach(all::addAll);
        return all;
    }

    private static List<List<String>> kinds(String word) {
        return List.of(lookAlike(word), voicing(word), longVowel(word), sokuon(word), smallKana(word));
    }

    /** シ ↔ ツ, ソ ↔ ン... */
    private static List<String> lookAlike(String word) {
        List<String> spellings = new ArrayList<>();
        for (int at = 0; at < word.length(); at++) {
            char c = word.charAt(at);
            for (String pair : LOOK_ALIKE) {
                int side = pair.indexOf(c);
                if (side >= 0) {
                    spellings.add(replace(word, at, pair.charAt(1 - side)));
                }
            }
        }
        return spellings;
    }

    /** Thiếu, thừa hoặc nhầm dấu ゛゜: カ ↔ ガ, ハ ↔ バ ↔ パ. */
    private static List<String> voicing(String word) {
        List<String> spellings = new ArrayList<>();
        for (int at = 0; at < word.length(); at++) {
            char c = word.charAt(at);
            int plain = PLAIN.indexOf(c);
            int voiced = VOICED.indexOf(c);
            if (plain >= 0) {
                spellings.add(replace(word, at, VOICED.charAt(plain)));
            }
            if (voiced >= 0) {
                spellings.add(replace(word, at, PLAIN.charAt(voiced)));
            }
            int h = H_ROW.indexOf(c);
            int b = B_ROW.indexOf(c);
            int p = P_ROW.indexOf(c);
            if (h >= 0 || b >= 0) {
                spellings.add(replace(word, at, P_ROW.charAt(Math.max(h, b))));
            }
            if (p >= 0) {
                spellings.add(replace(word, at, H_ROW.charAt(p)));
                spellings.add(replace(word, at, B_ROW.charAt(p)));
            }
        }
        return spellings;
    }

    /** Thiếu hoặc thừa trường âm: コーヒー → コヒー, バス → バース. */
    private static List<String> longVowel(String word) {
        List<String> spellings = new ArrayList<>();
        for (int at = 0; at < word.length(); at++) {
            if (word.charAt(at) == LONG_VOWEL) {
                spellings.add(word.substring(0, at) + word.substring(at + 1));
            }
        }
        for (int at = 0; at < word.length() - 1; at++) {
            char c = word.charAt(at);
            char next = word.charAt(at + 1);
            if (c != LONG_VOWEL && c != SOKUON && c != N && next != LONG_VOWEL && SMALL.indexOf(next) < 0) {
                spellings.add(word.substring(0, at + 1) + LONG_VOWEL + word.substring(at + 1));
            }
        }
        return spellings;
    }

    /** Thiếu hoặc thừa âm ngắt: ベッド → ベド, バス → バッス. */
    private static List<String> sokuon(String word) {
        List<String> spellings = new ArrayList<>();
        for (int at = 0; at < word.length(); at++) {
            if (word.charAt(at) == SOKUON) {
                spellings.add(word.substring(0, at) + word.substring(at + 1));
            }
        }
        for (int at = 1; at < word.length(); at++) {
            char before = word.charAt(at - 1);
            if (AFTER_SOKUON.indexOf(word.charAt(at)) >= 0 && before != LONG_VOWEL && before != N
                    && SMALL.indexOf(before) < 0) {
                spellings.add(word.substring(0, at) + SOKUON + word.substring(at));
            }
        }
        return spellings;
    }

    /** Chữ nhỏ viết thành chữ to: シャツ → シヤツ, ベッド → ベツド. */
    private static List<String> smallKana(String word) {
        List<String> spellings = new ArrayList<>();
        for (int at = 0; at < word.length(); at++) {
            int small = SMALL.indexOf(word.charAt(at));
            if (small >= 0) {
                spellings.add(replace(word, at, LARGE.charAt(small)));
            }
        }
        return spellings;
    }

    private static String replace(String word, int at, char c) {
        return word.substring(0, at) + c + word.substring(at + 1);
    }

    private static boolean isKatakana(char c) {
        return c == LONG_VOWEL || c >= 'ァ' && c <= 'ヺ';
    }
}
