package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Các chỗ cần sửa trong kho từ vựng.
 *
 * @param words              số từ trong kho
 * @param strokeMismatches   từ có số nét khác tổng số nét các chữ (bảng kanji_strokes)
 * @param unknownCharacters  chữ Hán có trong kho từ mà chưa có số nét - từ chứa chúng không tự tính được số nét
 * @param hanVietConflicts   chữ Hán được ghi nhiều âm Hán Việt ở các từ khác nhau (込 VÀO / NHẬP) - có thể đúng
 *                           (行 HÀNG / HÀNH) hoặc là ghi chưa thống nhất
 * @param missingHanViet     từ có chữ Hán mà chưa có âm Hán Việt
 */
public record VocabularyQualityReport(int words, List<StrokeMismatch> strokeMismatches, List<String> unknownCharacters,
                                      List<HanVietConflict> hanVietConflicts, List<WordRef> missingHanViet) {

    public record StrokeMismatch(Long id, String character, int stored, int computed) {
    }

    public record WordRef(Long id, String character, String hanViet) {
    }

    /** @param variants mỗi âm một mục, kèm các từ ghi âm đó */
    public record HanVietConflict(String character, List<Variant> variants) {
    }

    public record Variant(String hanViet, List<WordRef> words) {
    }

    /**
     * Kết quả sửa số nét theo bảng.
     *
     * @param dryRun  chỉ thử, chưa ghi
     * @param changed số từ được sửa (hoặc sẽ được sửa)
     */
    public record StrokeFix(boolean dryRun, int changed) {
    }
}
