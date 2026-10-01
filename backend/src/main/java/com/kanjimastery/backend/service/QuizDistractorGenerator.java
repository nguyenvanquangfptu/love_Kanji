package com.kanjimastery.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Sinh đáp án nhiễu "gần đúng" cho trắc nghiệm, thay vì lấy bừa một từ khác trong bài
 * (khác nhau quá nhiều nên đoán được ngay):
 * <ul>
 *   <li>Cách viết: thay một chữ Hán trong từ bằng chữ trông gần giống, theo các nhóm trong
 *       {@code quiz/similar-kanji.txt} (vd. 検査 → 険査, 験査).</li>
 *   <li>Cách đọc: sửa đúng một chỗ ở các điểm người học hay nhầm - trường âm, âm ngắt っ, âm đục/bán đục,
 *       âm ghép ゃゅょ (vd. しゅうへん → しゅへん, しょうへん, しゅうべん).</li>
 * </ul>
 * Chỉ trả về ứng viên theo thứ tự ưu tiên; loại từ đồng âm có thật và bù khi thiếu là việc của {@link QuizService}.
 */
@Slf4j
@Component
public class QuizDistractorGenerator {

    private static final String SIMILAR_KANJI_RESOURCE = "quiz/similar-kanji.txt";

    /** Hàng い - trừ chính い (index 0), các âm này mới ghép được với ゃゅょ. */
    private static final String I_ROW = "いきしちにひみりぎじぢびぴ";
    private static final String U_ROW = "うくすつぬふむゆるぐずづぶぷゅ";
    private static final String E_ROW = "えけせてねへめれげぜでべぺ";
    private static final String O_ROW = "おこそとのほもよろをごぞどぼぽょ";
    private static final String VOWELS = "あいうえお";
    private static final String SMALL_KANA = "ぁぃぅぇぉゃゅょゎっ";
    private static final String SMALL_YA_YU_YO = "ゃゅょ";
    private static final String BIG_YA_YU_YO = "やゆよ";
    /** Âm có thể đứng sau âm ngắt っ trong từ gốc Hán/thuần Nhật (hàng か, さ, た, ぱ). */
    private static final String SOKUON_FOLLOWERS = "かきくけこさしすせそたちつてとぱぴぷぺぽ";
    /** Âm cuối của chữ Hán hay biến thành っ khi ghép từ (学 がく + 校 → がっこう). */
    private static final String SOKUON_SOURCES = "くつちき";
    /** Âm thứ hai của một âm Hán hai âm tiết (めん, せつ, こく...) - đứng sau thì âm trước chưa phải cuối chữ. */
    private static final String MORA_CLOSING_ON_READING = "んくつちき";
    private static final Map<Character, String> VOICING = voicingPairs();

    /** Mỗi chữ Hán -> các chữ trông gần giống (không chứa chính nó), theo code point. */
    private final Map<Integer, List<Integer>> lookAlikes;

    public QuizDistractorGenerator() {
        Map<Integer, Set<Integer>> groups = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SIMILAR_KANJI_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        List<Integer> members = line.codePoints().filter(QuizDistractorGenerator::isKanji)
                                .distinct().boxed().toList();
                        for (Integer member : members) {
                            Set<Integer> similar = groups.computeIfAbsent(member, key -> new LinkedHashSet<>());
                            members.stream().filter(other -> !other.equals(member)).forEach(similar::add);
                        }
                    });
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được " + SIMILAR_KANJI_RESOURCE, ex);
        }
        lookAlikes = groups.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        log.info("Trắc nghiệm: {} chữ Hán có chữ trông gần giống để làm đáp án nhiễu", lookAlikes.size());
    }

    static boolean isKanji(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    /**
     * Các cách viết sai trông gần giống {@code word}. Thay đúng MỘT chữ Hán đứng trước, luân phiên giữa các vị trí
     * để ba đáp án không cùng sai một chữ; sau đó mới tới các cách thay HAI chữ cho từ có ít chữ dễ nhầm.
     */
    public List<String> lookAlikeSpellings(String word) {
        int[] chars = word.codePoints().toArray();
        List<Integer> positions = new ArrayList<>();
        List<List<String>> singleByPosition = new ArrayList<>();
        for (int i = 0; i < chars.length; i++) {
            List<Integer> similar = lookAlikes.get(chars[i]);
            if (similar == null) {
                continue;
            }
            positions.add(i);
            List<String> variants = new ArrayList<>();
            for (int replacement : similar) {
                variants.add(replaceCodePoints(chars, Map.of(i, replacement)));
            }
            Collections.shuffle(variants);
            singleByPosition.add(variants);
        }
        Collections.shuffle(singleByPosition);

        List<String> doubles = new ArrayList<>();
        for (int a = 0; a < positions.size(); a++) {
            for (int b = a + 1; b < positions.size(); b++) {
                int first = positions.get(a);
                int second = positions.get(b);
                for (int x : lookAlikes.get(chars[first])) {
                    for (int y : lookAlikes.get(chars[second])) {
                        doubles.add(replaceCodePoints(chars, Map.of(first, x, second, y)));
                    }
                }
            }
        }
        Collections.shuffle(doubles);

        Set<String> result = new LinkedHashSet<>(interleave(singleByPosition));
        result.addAll(doubles);
        result.remove(word);
        return List.copyOf(result);
    }

    /**
     * Các cách đọc sai nghe gần giống {@code reading}, mỗi cách sửa đúng một chỗ. Luân phiên giữa các kiểu bẫy
     * (trường âm, âm ngắt, âm đục, âm ghép) theo thứ tự ngẫu nhiên; thêm/bớt ん chỉ dùng khi các kiểu kia không đủ.
     * Không đụng vào okurigana/kana viết sẵn trong từ (埋める → chỉ sửa phần う của 埋).
     * <p>
     * Kiểu CHÈN thêm âm (こく → こうく, ちこく → ちっこく) chỉ áp dụng cho từ ghép âm Hán (≥ 2 chữ Hán, không okurigana):
     * với từ đọc kiểu kun như 表れる, 悔しい nó chỉ tạo ra chuỗi vô nghĩa (あらんわれる, くうやしい).
     */
    public List<String> trapReadings(String reading, String word) {
        if (reading == null || reading.isBlank()) {
            return List.of();
        }
        // Cách đọc chữ đơn có đuôi ghi trong ngoặc, vd. "み(る)" - chỉ sửa phần trước ngoặc.
        int paren = reading.indexOf('(');
        String base = paren > 0 ? reading.substring(0, paren) : reading;
        String tail = paren > 0 ? reading.substring(paren) : "";
        int prefix = commonPrefixLength(base, word);
        int suffix = paren > 0 ? 0 : commonSuffixLength(base, word, prefix);
        if (prefix + suffix >= base.length()) {
            return List.of();
        }
        String head = base.substring(0, prefix);
        String core = base.substring(prefix, base.length() - suffix);
        String rest = base.substring(base.length() - suffix) + tail;
        boolean coreEndsWord = rest.isEmpty();
        boolean sinoCompound = coreEndsWord && prefix == 0 && word.codePoints().filter(QuizDistractorGenerator::isKanji).count() >= 2;

        List<List<String>> primary = new ArrayList<>();
        for (Consumer<Consumer<String>> trap : List.<Consumer<Consumer<String>>>of(
                out -> longVowelTraps(core, sinoCompound, out),
                out -> sokuonTraps(core, sinoCompound, out),
                out -> voicingTraps(core, out),
                out -> youonTraps(core, out))) {
            primary.add(collect(trap, head, rest, reading));
        }
        Collections.shuffle(primary);

        Set<String> result = new LinkedHashSet<>(interleave(primary));
        List<String> moraicN = collect(out -> moraicNTraps(core, sinoCompound, out), head, rest, reading);
        Collections.shuffle(moraicN);
        result.addAll(moraicN);
        return List.copyOf(result);
    }

    private static List<String> collect(Consumer<Consumer<String>> trap, String head, String rest, String original) {
        Set<String> variants = new LinkedHashSet<>();
        trap.accept(core -> {
            String candidate = head + core + rest;
            if (!candidate.equals(original) && isPlausibleReading(candidate)) {
                variants.add(candidate);
            }
        });
        List<String> shuffled = new ArrayList<>(variants);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    /** こう ↔ こ, しゅう ↔ しゅ, せい ↔ せ. Kéo dài chỉ thử ở hàng お, ゅ (cuối từ được) và hàng え (giữa từ). */
    private static void longVowelTraps(String s, boolean insertions, Consumer<String> out) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean oOrU = O_ROW.indexOf(c) >= 0 || U_ROW.indexOf(c) >= 0;
            boolean e = E_ROW.indexOf(c) >= 0;
            if (!oOrU && !e || VOWELS.indexOf(c) >= 0) {
                continue;
            }
            char lengthener = oOrU ? 'う' : 'い';
            boolean atEnd = i == s.length() - 1;
            char next = atEnd ? 0 : s.charAt(i + 1);
            if (next == lengthener) {
                out.accept(removeAt(s, i + 1));
                continue;
            }
            // Chỉ kéo dài âm đứng cuối cách đọc của một chữ (ど|りょく → どうりょく); âm theo sau là ん/く/つ/ち/き
            // thì vẫn cùng một chữ (めん, せつ, こく) - chèn vào ra めいん, せいつ, こうく không giống cách đọc nào.
            boolean canLengthen = O_ROW.indexOf(c) >= 0 || c == 'ゅ' || (e && !atEnd);
            boolean closesSyllable = atEnd || MORA_CLOSING_ON_READING.indexOf(next) < 0
                    && SMALL_KANA.indexOf(next) < 0 && next != 'ー';
            if (insertions && canLengthen && closesSyllable) {
                out.accept(insertAt(s, i + 1, lengthener));
            }
        }
    }

    /** がっこう → がこう / がつこう, もくてき → もってき, ちこく → ちっこく. */
    private static void sokuonTraps(String s, boolean insertions, Consumer<String> out) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'っ') {
                out.accept(removeAt(s, i));
                out.accept(replaceAt(s, i, 'つ'));
                out.accept(replaceAt(s, i, 'く'));
                continue;
            }
            if (i == s.length() - 1 || SOKUON_FOLLOWERS.indexOf(s.charAt(i + 1)) < 0) {
                continue;
            }
            if (i > 0 && SOKUON_SOURCES.indexOf(c) >= 0) {
                out.accept(replaceAt(s, i, 'っ'));
            }
            // Không chèn trước âm cuối từ (めんせつ → めんせっつ, ちこく → ちこっく trông không giống từ thật).
            boolean longVowelMark = i > 0 && (c == 'う' || c == 'い');
            if (insertions && i + 2 < s.length() && !longVowelMark && c != 'ん' && c != 'ー') {
                out.accept(insertAt(s, i + 1, 'っ'));
            }
        }
    }

    /** かく ↔ がく, ほん ↔ ぼん/ぽん. */
    private static void voicingTraps(String s, Consumer<String> out) {
        for (int i = 0; i < s.length(); i++) {
            String alternatives = VOICING.get(s.charAt(i));
            if (alternatives == null) {
                continue;
            }
            for (char alternative : alternatives.toCharArray()) {
                out.accept(replaceAt(s, i, alternative));
            }
        }
    }

    /** しゅう ↔ しょう, きゃく ↔ きょく, びょういん ↔ びよういん. */
    private static void youonTraps(String s, Consumer<String> out) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int small = SMALL_YA_YU_YO.indexOf(c);
            if (small >= 0) {
                boolean beforeLongU = i + 1 < s.length() && s.charAt(i + 1) == 'う';
                for (char other : SMALL_YA_YU_YO.toCharArray()) {
                    // "ゃう" không phải trường âm thật, chỉ đổi qua lại ゅう ↔ ょう.
                    if (other != c && !(beforeLongU && other == 'ゃ')) {
                        out.accept(replaceAt(s, i, other));
                    }
                }
                out.accept(replaceAt(s, i, BIG_YA_YU_YO.charAt(small)));
            } else {
                int big = BIG_YA_YU_YO.indexOf(c);
                if (big >= 0 && i > 0 && I_ROW.indexOf(s.charAt(i - 1)) > 0) {
                    out.accept(replaceAt(s, i, SMALL_YA_YU_YO.charAt(big)));
                }
            }
        }
    }

    /** かんたん → かたん, いがい → いんがい. */
    private static void moraicNTraps(String s, boolean insertions, Consumer<String> out) {
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            char next = s.charAt(i + 1);
            if (c == 'ん' && i > 0) {
                out.accept(removeAt(s, i));
            }
            boolean nextIsConsonant = VOWELS.indexOf(next) < 0 && BIG_YA_YU_YO.indexOf(next) < 0
                    && SMALL_KANA.indexOf(next) < 0 && next != 'ん' && next != 'ー';
            if (insertions && nextIsConsonant && c != 'ん' && c != 'っ' && c != 'ー') {
                out.accept(insertAt(s, i + 1, 'ん'));
            }
        }
    }

    /** Loại các chuỗi không thể là cách đọc tiếng Nhật: ゃゅょ đứng sau âm không thuộc hàng い, っ ở đầu/cuối... */
    static boolean isPlausibleReading(String reading) {
        String kana = reading.replaceAll("\\(.*\\)$", "");
        if (kana.isEmpty() || kana.charAt(0) == 'ん' || SMALL_KANA.indexOf(kana.charAt(0)) >= 0) {
            return false;
        }
        for (int i = 0; i < kana.length(); i++) {
            char c = kana.charAt(i);
            if ((c < 'ぁ' || c > 'ゖ') && c != 'ー') {
                return false;
            }
            if (SMALL_YA_YU_YO.indexOf(c) >= 0 && I_ROW.indexOf(kana.charAt(i - 1)) <= 0) {
                return false;
            }
            if (c == 'っ' && (i == kana.length() - 1 || SOKUON_FOLLOWERS.indexOf(kana.charAt(i + 1)) < 0)) {
                return false;
            }
        }
        return true;
    }

    private static Map<Character, String> voicingPairs() {
        Map<Character, String> pairs = new HashMap<>();
        String plain = "かきくけこさしすせそたてと";
        String voiced = "がぎぐげござじずぜぞだでど";
        for (int i = 0; i < plain.length(); i++) {
            pairs.put(plain.charAt(i), String.valueOf(voiced.charAt(i)));
            pairs.put(voiced.charAt(i), String.valueOf(plain.charAt(i)));
        }
        String h = "はひふへほ";
        String b = "ばびぶべぼ";
        String p = "ぱぴぷぺぽ";
        for (int i = 0; i < h.length(); i++) {
            pairs.put(h.charAt(i), "" + b.charAt(i) + p.charAt(i));
            pairs.put(b.charAt(i), "" + h.charAt(i) + p.charAt(i));
            pairs.put(p.charAt(i), "" + h.charAt(i) + b.charAt(i));
        }
        return Map.copyOf(pairs);
    }

    private static int commonPrefixLength(String reading, String word) {
        int length = 0;
        while (length < reading.length() && length < word.length() && reading.charAt(length) == word.charAt(length)) {
            length++;
        }
        return length;
    }

    private static int commonSuffixLength(String reading, String word, int reservedPrefix) {
        int length = 0;
        while (length < reading.length() - reservedPrefix && length < word.length()
                && reading.charAt(reading.length() - 1 - length) == word.charAt(word.length() - 1 - length)) {
            length++;
        }
        return length;
    }

    private static List<String> interleave(List<List<String>> lists) {
        List<String> result = new ArrayList<>();
        for (int round = 0; ; round++) {
            boolean added = false;
            for (List<String> list : lists) {
                if (round < list.size()) {
                    result.add(list.get(round));
                    added = true;
                }
            }
            if (!added) {
                return result;
            }
        }
    }

    private static String replaceCodePoints(int[] chars, Map<Integer, Integer> replacements) {
        int[] copy = chars.clone();
        replacements.forEach((index, codePoint) -> copy[index] = codePoint);
        return new String(copy, 0, copy.length);
    }

    private static String replaceAt(String s, int index, char c) {
        return s.substring(0, index) + c + s.substring(index + 1);
    }

    private static String insertAt(String s, int index, char c) {
        return s.substring(0, index) + c + s.substring(index);
    }

    private static String removeAt(String s, int index) {
        return s.substring(0, index) + s.substring(index + 1);
    }
}
