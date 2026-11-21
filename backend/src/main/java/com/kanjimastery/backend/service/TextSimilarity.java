package com.kanjimastery.backend.service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Độ giống nhau của hai đoạn chữ tiếng Nhật, để báo câu hỏi / bài đọc gần trùng với nội dung đã có. So theo các cặp hai
 * ký tự liền nhau: hai câu chỉ khác vài chữ (二年 → 三年, đổi chủ ngữ) vẫn chung gần hết các cặp. Điểm là hệ số Jaccard
 * của hai tập cặp ký tự - số cặp chung chia số cặp của cả hai - từ 0 (không chung cặp nào) tới 1 (như nhau).
 * <p>
 * Trước khi so, chữ được chuẩn hoá: full-width/half-width về một dạng (NFKC), bỏ số chỗ trống 【n】, đánh dấu gạch chân
 * 〔下線〕, rồi bỏ mọi thứ không phải chữ hay số (dấu câu, ô trống （　　）, ＿, ★, khoảng trắng) - hai câu chỉ khác những
 * thứ đó là như nhau. Đoạn quá ngắn dễ giống nhau tình cờ nên chỉ coi là giống khi như nhau hẳn.
 */
public final class TextSimilarity {

    /** Đoạn ngắn hơn số ký tự này (sau khi chuẩn hoá) chỉ được so xem có như nhau hẳn không. */
    public static final int MIN_LENGTH = 8;
    /** Số chỗ trống của đoạn văn 【1】【2a】 và đánh dấu gạch chân 〔下線〕〔/下線〕: bỏ cả cụm, không chỉ dấu ngoặc. */
    private static final Pattern MARKERS = Pattern.compile("【\\d+[ab]?】|〔/?下線〕");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private TextSimilarity() {
    }

    /** Chữ đã chuẩn hoá để so; chuỗi rỗng nếu {@code text} null hoặc không có chữ / số nào. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return NOT_LETTER_OR_DIGIT.matcher(MARKERS.matcher(unified).replaceAll("")).replaceAll("");
    }

    public static double similarity(String first, String second) {
        return Fingerprint.of(first).similarity(Fingerprint.of(second));
    }

    /**
     * Một đoạn chữ đã chuẩn hoá và tách sẵn các cặp ký tự, để so với nhiều đoạn khác mà không tách lại.
     *
     * @param normalized chữ đã chuẩn hoá ({@link #normalize})
     * @param length     số ký tự của chữ đã chuẩn hoá
     * @param bigrams    các cặp hai ký tự liền nhau (tính theo code point, đúng cả với chữ Hán ngoài BMP)
     */
    public record Fingerprint(String normalized, int length, Set<String> bigrams) {

        public static Fingerprint of(String text) {
            String normalized = normalize(text);
            int[] codePoints = normalized.codePoints().toArray();
            Set<String> bigrams = new HashSet<>();
            for (int i = 0; i + 1 < codePoints.length; i++) {
                bigrams.add(new String(codePoints, i, 2));
            }
            return new Fingerprint(normalized, codePoints.length, Set.copyOf(bigrams));
        }

        public boolean isEmpty() {
            return length == 0;
        }

        /**
         * 1 nếu hai đoạn như nhau sau khi chuẩn hoá; 0 nếu một đoạn rỗng, hoặc ngắn hơn {@link #MIN_LENGTH} mà không
         * như nhau; còn lại là hệ số Jaccard của hai tập cặp ký tự.
         */
        public double similarity(Fingerprint other) {
            if (isEmpty() || other.isEmpty()) {
                return 0;
            }
            if (normalized.equals(other.normalized)) {
                return 1;
            }
            if (length < MIN_LENGTH || other.length < MIN_LENGTH) {
                return 0;
            }
            Set<String> smaller = bigrams.size() <= other.bigrams.size() ? bigrams : other.bigrams;
            Set<String> larger = smaller == bigrams ? other.bigrams : bigrams;
            long shared = smaller.stream().filter(larger::contains).count();
            return (double) shared / (bigrams.size() + other.bigrams.size() - shared);
        }
    }
}
