package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Một từ tìm được.
 *
 * @param lessons  tên các bài có từ này (vd. N5-01)
 * @param inReview từ đã có trong lịch ôn của người đang tìm
 */
public record SearchResultResponse(Long id, String character, String reading, String hanViet, String meaning,
                                   String jlptLevel, List<String> lessons, boolean inReview) {
}
