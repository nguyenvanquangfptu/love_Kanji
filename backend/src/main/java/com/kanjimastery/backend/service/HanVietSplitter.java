package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.WordCharacter;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tách âm Hán Việt của một từ ra từng chữ Hán: 銀行 + "NGÂN HÀNG" → 銀 NGÂN, 行 HÀNG. Quy ước nhập từ vựng là mỗi chữ
 * Hán một âm tiết, kể cả 々 (人々 = NHÂN NHÂN), nên chỉ tách khi số chữ Hán bằng số âm tiết; âm của 々 được bỏ qua vì
 * nó không phải một chữ riêng. Đếm theo code point: chữ ngoài BMP như 𠮟 là hai char Java nhưng chỉ là một chữ.
 */
final class HanVietSplitter {

    /** Vì sao một từ không tách được. */
    enum Problem {
        /** Từ không có chữ Hán (テレビ, ありがとう): bình thường, không cần sửa. */
        NO_KANJI,
        NO_HAN_VIET,
        /** Số âm tiết khác số chữ Hán. */
        COUNT_MISMATCH,
        /** Có âm tiết không phải một tiếng chỉ gồm chữ cái (dấu ngoặc, gạch nối, quá dài...). */
        NOT_A_SYLLABLE
    }

    /**
     * @param parts   các chữ Hán (trừ 々) theo thứ tự, kèm âm Hán Việt; rỗng nếu không tách được
     * @param problem null nếu tách được
     */
    record Split(List<WordCharacter> parts, Problem problem) {

        static Split failed(Problem problem) {
            return new Split(List.of(), problem);
        }
    }

    private static final Pattern SYLLABLE = Pattern.compile("\\p{L}{1,20}");
    private static final int ITERATION_MARK = '々';

    private HanVietSplitter() {
    }

    static Split split(String word, String hanViet) {
        int[] kanji = word.codePoints().filter(QuizDistractorGenerator::isKanji).toArray();
        if (kanji.length == 0) {
            return Split.failed(Problem.NO_KANJI);
        }
        if (!StringUtils.hasText(hanViet)) {
            return Split.failed(Problem.NO_HAN_VIET);
        }
        String[] syllables = normalize(hanViet).split(" ");
        if (syllables.length != kanji.length) {
            return Split.failed(Problem.COUNT_MISMATCH);
        }
        List<WordCharacter> parts = new ArrayList<>(kanji.length);
        for (int i = 0; i < kanji.length; i++) {
            if (!SYLLABLE.matcher(syllables[i]).matches()) {
                return Split.failed(Problem.NOT_A_SYLLABLE);
            }
            if (kanji[i] != ITERATION_MARK) {
                parts.add(new WordCharacter(Character.toString(kanji[i]), syllables[i]));
            }
        }
        return new Split(List.copyOf(parts), null);
    }

    /** Dấu tiếng Việt gõ kiểu tổ hợp hay dựng sẵn đều về một dạng (NFC), viết hoa, một khoảng trắng giữa các âm. */
    static String normalize(String hanViet) {
        return Normalizer.normalize(hanViet, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
