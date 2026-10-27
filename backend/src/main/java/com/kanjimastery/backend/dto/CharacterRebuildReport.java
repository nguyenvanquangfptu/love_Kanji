package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Kết quả tách lại Hán Việt từng chữ cho cả kho từ.
 *
 * @param dryRun   chỉ thử: chưa ghi gì vào DB
 * @param words    số từ trong kho
 * @param split    số từ tách được
 * @param kanaOnly số từ không có chữ Hán (không cần tách)
 * @param changed  số từ có các chữ thành phần khác với đang lưu (đã ghi lại, hoặc sẽ ghi nếu chạy thật)
 * @param skipped  các từ có chữ Hán mà không tách được, cần sửa cột Hán Việt
 */
public record CharacterRebuildReport(boolean dryRun, int words, int split, int kanaOnly, int changed,
                                     List<SkippedWord> skipped) {

    /** @param reason NO_HAN_VIET, COUNT_MISMATCH (số âm khác số chữ Hán) hoặc NOT_A_SYLLABLE */
    public record SkippedWord(Long id, String character, String hanViet, String reason) {
    }
}
