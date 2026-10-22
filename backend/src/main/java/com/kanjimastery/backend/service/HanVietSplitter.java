package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.WordCharacter;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tách âm Hán Việt của một từ ra từng chữ Hán: 銀行 + "NGÂN HÀNG" → 銀 NGÂN, 行 HÀNG. Quy ước nhập từ vựng là mỗi chữ
 * Hán một âm tiết, nên chỉ tách khi số chữ Hán bằng số âm tiết. 々 không phải một chữ riêng nên không có phần của nó; kho
 * từ ghi nó theo cả hai cách (人々 = NHÂN, hoặc NHÂN NHÂN) nên âm của 々 có hay không đều được. Đếm theo code point:
 * chữ ngoài BMP như 𠮟 là hai char Java nhưng chỉ là một chữ.
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
        long iterationMarks = Arrays.stream(kanji).filter(codePoint -> codePoint == ITERATION_MARK).count();
        // 々 có âm riêng (NHÂN NHÂN) hay không (NHÂN).
        boolean marksHaveSyllables = syllables.length == kanji.length;
        if (!marksHaveSyllables && syllables.length != kanji.length - iterationMarks) {
            return Split.failed(Problem.COUNT_MISMATCH);
        }
        List<WordCharacter> parts = new ArrayList<>(kanji.length);
        int syllable = 0;
        for (int codePoint : kanji) {
            if (codePoint == ITERATION_MARK && !marksHaveSyllables) {
                continue;
            }
            if (!SYLLABLE.matcher(syllables[syllable]).matches()) {
                return Split.failed(Problem.NOT_A_SYLLABLE);
            }
            if (codePoint != ITERATION_MARK) {
                parts.add(new WordCharacter(Character.toString(codePoint), syllables[syllable]));
            }
            syllable++;
        }
        return new Split(List.copyOf(parts), null);
    }

    /** Dấu tiếng Việt gõ kiểu tổ hợp hay dựng sẵn đều về một dạng (NFC), viết hoa, một khoảng trắng giữa các âm. */
    static String normalize(String hanViet) {
        return Normalizer.normalize(hanViet, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
