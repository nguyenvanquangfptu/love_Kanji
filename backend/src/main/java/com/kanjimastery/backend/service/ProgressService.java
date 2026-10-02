package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.ProgressResponse;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.Activity;
import com.kanjimastery.backend.repository.ReviewLogRepository.QuizMistake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tiến bộ của người học, tính từ review_logs: tỉ lệ nhớ thật theo tuần, số lượt ôn và từ mới theo ngày, độ chính xác
 * trắc nghiệm theo hướng hỏi, và những chỗ hay nhầm nhất.
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    static final int WEEKS = 8;
    static final int DAYS = 14;
    static final int DIRECTION_DAYS = 30;
    static final int CONFUSION_DAYS = 90;
    static final int MAX_CONFUSIONS = 8;

    private final ReviewLogRepository reviewLogRepository;
    private final KanjiRepository kanjiRepository;
    private final StudyCalendar calendar;

    @Transactional(readOnly = true)
    public ProgressResponse get(Long userId) {
        LocalDate today = calendar.today();
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = thisWeek.minusWeeks(WEEKS - 1);
        LocalDate firstDay = today.minusDays(DAYS - 1);

        Map<LocalDate, long[]> weeks = new LinkedHashMap<>();
        for (int i = 0; i < WEEKS; i++) {
            weeks.put(firstWeek.plusWeeks(i), new long[2]);
        }
        Map<LocalDate, long[]> days = new LinkedHashMap<>();
        for (int i = 0; i < DAYS; i++) {
            days.put(firstDay.plusDays(i), new long[2]);
        }

        LocalDate since = firstWeek.isBefore(firstDay) ? firstWeek : firstDay;
        for (Activity answer : reviewLogRepository.activitySince(userId, calendar.startOf(since))) {
            LocalDate day = calendar.dayOf(answer.getReviewedAt());
            boolean scheduled = Boolean.TRUE.equals(answer.getScheduled());
            boolean newWord = scheduled && CardState.NEW.equals(answer.getStateBefore());

            long[] dayCounts = days.get(day);
            if (dayCounts != null) {
                dayCounts[newWord ? 1 : 0]++;
            }
            if (scheduled && CardState.REVIEW.equals(answer.getStateBefore())) {
                long[] weekCounts = weeks.get(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                if (weekCounts != null) {
                    weekCounts[0]++;
                    if (Boolean.TRUE.equals(answer.getCorrect())) {
                        weekCounts[1]++;
                    }
                }
            }
        }

        return ProgressResponse.builder()
                .weeks(weeks.entrySet().stream()
                        .map(week -> new ProgressResponse.Week(week.getKey(), week.getValue()[0], week.getValue()[1]))
                        .toList())
                .days(days.entrySet().stream()
                        .map(day -> new ProgressResponse.Day(day.getKey(), day.getValue()[0], day.getValue()[1]))
                        .toList())
                .directions(reviewLogRepository.quizDirectionStats(userId, calendar.now().minusDays(DIRECTION_DAYS)).stream()
                        .filter(row -> row.getDirection() != null)
                        .map(row -> new ProgressResponse.Direction(row.getDirection(), row.getAnswers(),
                                row.getAnswers() - row.getErrors()))
                        .toList())
                .confusions(confusions(userId))
                .build();
    }

    private List<ProgressResponse.Confusion> confusions(Long userId) {
        List<QuizMistake> mistakes = reviewLogRepository.topQuizMistakes(userId,
                calendar.now().minusDays(CONFUSION_DAYS), MAX_CONFUSIONS);
        Map<Long, Kanji> words = kanjiRepository.findAllById(mistakes.stream().map(QuizMistake::getKanjiId).toList())
                .stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        List<ProgressResponse.Confusion> result = new ArrayList<>();
        for (QuizMistake mistake : mistakes) {
            Kanji word = words.get(mistake.getKanjiId());
            if (word != null) {
                result.add(new ProgressResponse.Confusion(word.getId(), word.getCharacter(), word.getReading(),
                        word.getMeaning(), mistake.getDirection(), mistake.getChosenAnswer(), mistake.getTimes()));
            }
        }
        return result;
    }
}
