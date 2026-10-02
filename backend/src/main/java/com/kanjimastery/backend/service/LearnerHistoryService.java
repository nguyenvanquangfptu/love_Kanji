package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.DirectionStats;
import com.kanjimastery.backend.repository.ReviewLogRepository.QuizMistake;
import com.kanjimastery.backend.repository.ReviewLogRepository.WordDirectionStats;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import com.kanjimastery.backend.service.LearnerHistory.Tally;
import com.kanjimastery.backend.service.LearnerHistory.WordHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Đọc lịch sử học ({@link LearnerHistory}, đáp án từng chọn sai) cho cả một nhóm từ, không truy vấn theo từng từ. */
@Service
@RequiredArgsConstructor
public class LearnerHistoryService {

    /** Sai trong chừng này ngày thì coi là đang yếu. */
    static final Duration RECENT_ERROR_WINDOW = Duration.ofDays(14);
    /** Tỉ lệ sai theo hướng hỏi tính trên chừng này ngày gần nhất. */
    static final Duration DIRECTION_WINDOW = Duration.ofDays(30);

    private final UserKanjiSrsRepository srsRepository;
    private final ReviewLogRepository reviewLogRepository;

    @Transactional(readOnly = true)
    public LearnerHistory load(Long userId, Collection<Long> kanjiIds) {
        LocalDateTime now = LocalDateTime.now();
        if (kanjiIds.isEmpty()) {
            return LearnerHistory.empty(now);
        }

        Map<Long, UserKanjiSrs> cards = srsRepository.findAllByUserIdAndKanjiIdIn(userId, kanjiIds).stream()
                .collect(Collectors.toMap(UserKanjiSrs::getKanjiId, Function.identity()));

        Map<Long, Map<String, Tally>> byDirection = new HashMap<>();
        Map<Long, Long> recentErrors = new HashMap<>();
        for (WordDirectionStats row : reviewLogRepository.wordDirectionStats(userId, kanjiIds, now.minus(RECENT_ERROR_WINDOW))) {
            Map<String, Tally> directions = byDirection.computeIfAbsent(row.getKanjiId(), id -> new HashMap<>());
            if (row.getDirection() != null) {
                directions.put(row.getDirection(), new Tally(row.getAnswers(), row.getErrors()));
            }
            recentErrors.merge(row.getKanjiId(), row.getRecentErrors(), Long::sum);
        }
        Map<Long, WordHistory> words = new HashMap<>();
        byDirection.forEach((kanjiId, directions) ->
                words.put(kanjiId, new WordHistory(directions, recentErrors.getOrDefault(kanjiId, 0L))));

        Map<String, Tally> learnerDirections = reviewLogRepository.quizDirectionStats(userId, now.minus(DIRECTION_WINDOW))
                .stream()
                .filter(row -> row.getDirection() != null)
                .collect(Collectors.toMap(DirectionStats::getDirection, row -> new Tally(row.getAnswers(), row.getErrors())));

        return new LearnerHistory(now, cards, words, learnerDirections);
    }

    /** Một đáp án sai người học từng chọn và số lần chọn. */
    public record PastMistake(String answer, long times) {
    }

    /**
     * Đáp án sai người học từng chọn trong trắc nghiệm: kanjiId -> hướng hỏi -> các đáp án, chọn nhiều lần nhất trước.
     * Một truy vấn cho cả bài.
     */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, List<PastMistake>>> pastMistakes(Long userId, Collection<Long> kanjiIds) {
        if (kanjiIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, List<PastMistake>>> result = new HashMap<>();
        for (QuizMistake row : reviewLogRepository.quizMistakes(userId, kanjiIds)) {
            result.computeIfAbsent(row.getKanjiId(), id -> new HashMap<>())
                    .computeIfAbsent(row.getDirection(), direction -> new ArrayList<>())
                    .add(new PastMistake(row.getChosenAnswer(), row.getTimes()));
        }
        return result;
    }
}
