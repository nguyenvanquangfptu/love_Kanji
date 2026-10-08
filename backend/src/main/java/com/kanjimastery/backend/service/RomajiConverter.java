package com.kanjimastery.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuyển cách đọc người học gõ thành hiragana. Nhận cả kiểu Hepburn lẫn kiểu gõ bàn phím (shi/si, tsu/tu, ja/zya...),
 * phụ âm đôi là っ, ん viết {@code n} trước phụ âm hoặc ở cuối, {@code n'} hoặc {@code nn} (trước nguyên âm thì {@code nn}
 * là ん + âm tiết hàng n như Hepburn: {@code sannin} = さんにん), {@code -} là trường âm ー. Đầu vào đã là
 * kana (bàn phím tiếng Nhật) được giữ nguyên, katakana đổi sang hiragana.
 */
public final class RomajiConverter {

    /** Âm tiết romaji → kana, khớp chuỗi dài nhất trước. */
    private static final Map<String, String> SYLLABLES = new HashMap<>();
    private static final int LONGEST = 4;
    private static final Pattern DOUBLE_N = Pattern.compile("(?<![n'])nn(?=[aiueoy])");

    static {
        String[][] rows = {
                {"a", "あ"}, {"i", "い"}, {"u", "う"}, {"e", "え"}, {"o", "お"},
                {"ka", "か"}, {"ki", "き"}, {"ku", "く"}, {"ke", "け"}, {"ko", "こ"},
                {"kya", "きゃ"}, {"kyu", "きゅ"}, {"kyo", "きょ"},
                {"ga", "が"}, {"gi", "ぎ"}, {"gu", "ぐ"}, {"ge", "げ"}, {"go", "ご"},
                {"gya", "ぎゃ"}, {"gyu", "ぎゅ"}, {"gyo", "ぎょ"},
                {"sa", "さ"}, {"si", "し"}, {"shi", "し"}, {"su", "す"}, {"se", "せ"}, {"so", "そ"},
                {"sha", "しゃ"}, {"shu", "しゅ"}, {"she", "しぇ"}, {"sho", "しょ"},
                {"sya", "しゃ"}, {"syu", "しゅ"}, {"sye", "しぇ"}, {"syo", "しょ"},
                {"za", "ざ"}, {"zi", "じ"}, {"ji", "じ"}, {"zu", "ず"}, {"ze", "ぜ"}, {"zo", "ぞ"},
                {"ja", "じゃ"}, {"ju", "じゅ"}, {"je", "じぇ"}, {"jo", "じょ"},
                {"jya", "じゃ"}, {"jyu", "じゅ"}, {"jye", "じぇ"}, {"jyo", "じょ"},
                {"zya", "じゃ"}, {"zyu", "じゅ"}, {"zye", "じぇ"}, {"zyo", "じょ"},
                {"ta", "た"}, {"ti", "ち"}, {"chi", "ち"}, {"tu", "つ"}, {"tsu", "つ"}, {"te", "て"}, {"to", "と"},
                {"cha", "ちゃ"}, {"chu", "ちゅ"}, {"che", "ちぇ"}, {"cho", "ちょ"},
                {"tya", "ちゃ"}, {"tyu", "ちゅ"}, {"tye", "ちぇ"}, {"tyo", "ちょ"},
                {"cya", "ちゃ"}, {"cyu", "ちゅ"}, {"cye", "ちぇ"}, {"cyo", "ちょ"},
                {"thi", "てぃ"}, {"dhi", "でぃ"}, {"twu", "とぅ"}, {"dwu", "どぅ"},
                {"da", "だ"}, {"di", "ぢ"}, {"du", "づ"}, {"dzu", "づ"}, {"de", "で"}, {"do", "ど"},
                {"dya", "ぢゃ"}, {"dyu", "ぢゅ"}, {"dyo", "ぢょ"},
                {"na", "な"}, {"ni", "に"}, {"nu", "ぬ"}, {"ne", "ね"}, {"no", "の"},
                {"nya", "にゃ"}, {"nyu", "にゅ"}, {"nyo", "にょ"},
                {"ha", "は"}, {"hi", "ひ"}, {"hu", "ふ"}, {"fu", "ふ"}, {"he", "へ"}, {"ho", "ほ"},
                {"hya", "ひゃ"}, {"hyu", "ひゅ"}, {"hyo", "ひょ"},
                {"fa", "ふぁ"}, {"fi", "ふぃ"}, {"fe", "ふぇ"}, {"fo", "ふぉ"},
                {"ba", "ば"}, {"bi", "び"}, {"bu", "ぶ"}, {"be", "べ"}, {"bo", "ぼ"},
                {"bya", "びゃ"}, {"byu", "びゅ"}, {"byo", "びょ"},
                {"pa", "ぱ"}, {"pi", "ぴ"}, {"pu", "ぷ"}, {"pe", "ぺ"}, {"po", "ぽ"},
                {"pya", "ぴゃ"}, {"pyu", "ぴゅ"}, {"pyo", "ぴょ"},
                {"ma", "ま"}, {"mi", "み"}, {"mu", "む"}, {"me", "め"}, {"mo", "も"},
                {"mya", "みゃ"}, {"myu", "みゅ"}, {"myo", "みょ"},
                {"ya", "や"}, {"yu", "ゆ"}, {"yo", "よ"},
                {"ra", "ら"}, {"ri", "り"}, {"ru", "る"}, {"re", "れ"}, {"ro", "ろ"},
                {"rya", "りゃ"}, {"ryu", "りゅ"}, {"ryo", "りょ"},
                {"la", "ら"}, {"li", "り"}, {"lu", "る"}, {"le", "れ"}, {"lo", "ろ"},
                {"wa", "わ"}, {"wi", "うぃ"}, {"we", "うぇ"}, {"wo", "を"},
                {"va", "ゔぁ"}, {"vi", "ゔぃ"}, {"vu", "ゔ"}, {"ve", "ゔぇ"}, {"vo", "ゔぉ"},
                {"xa", "ぁ"}, {"xi", "ぃ"}, {"xu", "ぅ"}, {"xe", "ぇ"}, {"xo", "ぉ"},
                {"xya", "ゃ"}, {"xyu", "ゅ"}, {"xyo", "ょ"}, {"xtu", "っ"}, {"xtsu", "っ"}, {"ltu", "っ"},
                {"ltsu", "っ"}, {"xwa", "ゎ"}, {"-", "ー"},
        };
        for (String[] row : rows) {
            SYLLABLES.put(row[0], row[1]);
        }
    }

    private RomajiConverter() {
    }

    /**
     * Mọi cách hiểu hiragana của {@code input}; rỗng nếu có chữ không chuyển được. Nhiều hơn một cách chỉ khi có nguyên
     * âm dài viết bằng dấu: ō là おう hoặc おお, ē là えい hoặc ええ (ā, ī, ū chỉ có một cách).
     */
    public static List<String> toHiragana(String input) {
        if (input == null) {
            return List.of();
        }
        String text = input.strip().replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('ー', '-').replace('－', '-').replace('—', '-');
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> variants = new ArrayList<>(List.of(text));
        variants = expand(variants, "ā", "aa");
        variants = expand(variants, "â", "aa");
        variants = expand(variants, "ī", "ii");
        variants = expand(variants, "î", "ii");
        variants = expand(variants, "ū", "uu");
        variants = expand(variants, "û", "uu");
        variants = expand(variants, "ō", "ou", "oo");
        variants = expand(variants, "ô", "ou", "oo");
        variants = expand(variants, "ē", "ei", "ee");
        variants = expand(variants, "ê", "ei", "ee");
        variants = expandDoubleN(variants);
        List<String> result = new ArrayList<>();
        for (String variant : variants) {
            convert(variant).filter(kana -> !result.contains(kana)).ifPresent(result::add);
        }
        return result;
    }

    /**
     * "nn" trước nguyên âm có hai cách hiểu: kiểu bàn phím "hanni" là はんい (nn = ん), kiểu Hepburn "sannin" là さんにん
     * (ん + hàng n). Tách thành hai cách viết rõ ràng bằng n'. "nnn" (konnnichiwa) chỉ có một cách hiểu nên để nguyên.
     */
    private static List<String> expandDoubleN(List<String> variants) {
        List<String> result = variants;
        for (int round = 0; round < 6 && result.stream().anyMatch(v -> DOUBLE_N.matcher(v).find()); round++) {
            List<String> next = new ArrayList<>();
            for (String variant : result) {
                Matcher matcher = DOUBLE_N.matcher(variant);
                if (!matcher.find()) {
                    next.add(variant);
                    continue;
                }
                String before = variant.substring(0, matcher.start());
                String after = variant.substring(matcher.end());
                next.add(before + "n'n" + after);
                next.add(before + "n'" + after);
            }
            result = next;
        }
        return result;
    }

    /** Thay mọi lần xuất hiện của {@code mark} bằng từng cách viết - mỗi chỗ chọn riêng, tối đa 64 tổ hợp. */
    private static List<String> expand(List<String> variants, String mark, String... spellings) {
        List<String> result = variants;
        while (result.stream().anyMatch(variant -> variant.contains(mark)) && result.size() < 64) {
            List<String> next = new ArrayList<>();
            for (String variant : result) {
                int at = variant.indexOf(mark);
                if (at < 0) {
                    next.add(variant);
                    continue;
                }
                for (String spelling : spellings) {
                    next.add(variant.substring(0, at) + spelling + variant.substring(at + mark.length()));
                }
            }
            result = next;
        }
        return result;
    }

    private static Optional<String> convert(String text) {
        StringBuilder kana = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (isKana(c)) {
                kana.append(toHiragana(c));
                i++;
                continue;
            }
            // Phụ âm đôi (kitte, matcha qua "tch") là âm ngắt っ.
            if (i + 1 < text.length() && isConsonant(c) && c != 'n' && c != 'm'
                    && (text.charAt(i + 1) == c || c == 't' && text.charAt(i + 1) == 'c')) {
                kana.append('っ');
                i++;
                continue;
            }
            // Hepburn cũ viết ん trước b, p, m là m: shimbun = しんぶん, semmon = せんもん.
            if (c == 'm' && i + 1 < text.length() && "bpm".indexOf(text.charAt(i + 1)) >= 0) {
                kana.append('ん');
                i++;
                continue;
            }
            if (c == 'n') {
                char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
                if (next == '\'') {
                    kana.append('ん');
                    i += 2;
                    continue;
                }
                if (next == 'n') {
                    // "nn" trước nguyên âm đã được tách ở expandDoubleN; còn lại (honn, konnnichiwa) là ん.
                    kana.append('ん');
                    i += 2;
                    continue;
                }
                if (next == 0 || "aiueoy".indexOf(next) < 0) {
                    kana.append('ん');
                    i++;
                    continue;
                }
            }
            String matched = null;
            for (int length = Math.min(LONGEST, text.length() - i); length > 0; length--) {
                String piece = text.substring(i, i + length);
                if (SYLLABLES.containsKey(piece)) {
                    matched = piece;
                    break;
                }
            }
            if (matched == null) {
                return Optional.empty();
            }
            kana.append(SYLLABLES.get(matched));
            i += matched.length();
        }
        return Optional.of(kana.toString());
    }

    private static boolean isConsonant(char c) {
        return c >= 'a' && c <= 'z' && "aiueo".indexOf(c) < 0;
    }

    static boolean isKana(char c) {
        return c >= 'ぁ' && c <= 'ゖ' || c >= 'ァ' && c <= 'ヶ' || c == 'ー' || c == 'ゔ';
    }

    /** Katakana → hiragana; chữ khác giữ nguyên. */
    static char toHiragana(char c) {
        return c >= 'ァ' && c <= 'ヶ' ? (char) (c - ('ァ' - 'ぁ')) : c;
    }

    /** Đổi mọi katakana trong chuỗi sang hiragana. */
    public static String katakanaToHiragana(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            result.append(toHiragana(c));
        }
        return result.toString();
    }
}
