package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Một chữ Hán và các từ trong kho có chứa nó, gom theo âm Hán Việt của chữ trong từng từ (行: HÀNH - 行動, 旅行; HÀNG -
 * 銀行). Âm có nhiều từ đứng trước.
 */
public record CharacterDetailResponse(String character, List<Reading> readings) {

    public record Reading(String hanViet, List<Word> words) {
    }

    /**
     * @param hanViet  âm Hán Việt của cả từ
     * @param inReview từ đã có trong lịch ôn của người đang xem
     */
    public record Word(Long id, String character, String reading, String hanViet, String meaning, String jlptLevel,
                       boolean inReview) {
    }
}
