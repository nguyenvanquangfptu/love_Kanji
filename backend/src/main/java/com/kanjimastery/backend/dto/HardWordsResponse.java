package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class HardWordsResponse {
    /** Quên từ chừng này lần trở lên là từ khó. */
    private int lapseThreshold;
    /** Quên nhiều lần nhất trước. */
    private List<Word> words;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Word {
        private KanjiResponse kanji;
        private int lapseCount;
        private LocalDateTime nextReviewAt;
        private String personalNote;
    }
}
