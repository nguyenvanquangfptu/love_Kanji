package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Một câu có dùng từ đang học, hiện ở mặt sau thẻ ôn.
 *
 * @param sentence câu đầy đủ (để đọc to)
 * @param segments câu chia đoạn kèm furigana; đoạn {@code target} là chỗ có từ đó
 * @param source   {@code EXAMPLE}: câu ví dụ của từ; {@code EXAM}: câu trong ngân hàng đề có hỏi từ đó
 */
public record WordSentenceResponse(String sentence, List<RubySegment> segments, String source) {

    public static final String EXAMPLE = "EXAMPLE";
    public static final String EXAM = "EXAM";
}
