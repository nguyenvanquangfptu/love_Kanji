package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.UserKanjiSrs;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Lịch sử học của một người với một nhóm từ (cả bài hoặc cả cấp độ đang làm trắc nghiệm), đọc một lần bằng
 * {@link LearnerHistoryService}.
 *
 * @param cards      thẻ ôn tập theo kanjiId (chỉ những từ đã có trong lịch ôn)
 * @param words      lượt trả lời theo kanjiId (chỉ những từ đã từng trả lời)
 * @param directions lượt trả lời trắc nghiệm của người học theo từng hướng hỏi, trên mọi từ, trong 30 ngày gần đây
 */
public record LearnerHistory(LocalDateTime now,
                             Map<Long, UserKanjiSrs> cards,
                             Map<Long, WordHistory> words,
                             Map<String, Tally> directions) {

    /** Số lần trả lời và số lần sai. */
    public record Tally(long answers, long errors) {
        public static final Tally NONE = new Tally(0, 0);
    }

    /**
     * @param byDirection  lượt trả lời trắc nghiệm theo từng hướng hỏi
     * @param recentErrors số lần sai gần đây, tính cả "Quên" khi lật thẻ
     */
    public record WordHistory(Map<String, Tally> byDirection, long recentErrors) {
    }

    public static LearnerHistory empty(LocalDateTime now) {
        return new LearnerHistory(now, Map.of(), Map.of(), Map.of());
    }

    /** Thẻ ôn tập của từ, null nếu từ chưa có trong lịch ôn. */
    public UserKanjiSrs card(Long kanjiId) {
        return cards.get(kanjiId);
    }

    /** Đã từng gặp: có trong lịch ôn hoặc đã từng trả lời. */
    public boolean seen(Long kanjiId) {
        return cards.containsKey(kanjiId) || words.containsKey(kanjiId);
    }

    public long recentErrors(Long kanjiId) {
        WordHistory word = words.get(kanjiId);
        return word == null ? 0 : word.recentErrors();
    }

    public Tally tally(Long kanjiId, String direction) {
        WordHistory word = words.get(kanjiId);
        return word == null ? Tally.NONE : word.byDirection().getOrDefault(direction, Tally.NONE);
    }

    public Tally directionTally(String direction) {
        return directions.getOrDefault(direction, Tally.NONE);
    }
}
