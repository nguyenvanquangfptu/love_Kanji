package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.ProgressResponse;
import com.kanjimastery.backend.dto.StreakResponse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tiến bộ của người học, tính từ review_logs: tỉ lệ nhớ thật theo tuần, số lượt ôn và từ mới theo ngày, độ chính xác
 * trắc nghiệm theo hướng hỏi, những chỗ hay nhầm nhất, và FSRS đoán trí nhớ của người học sát tới đâu.
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    static final int WEEKS = 8;
    static final int DAYS = 14;
    static final int DIRECTION_DAYS = 30;
    static final int CONFUSION_DAYS = 90;
    static final int MAX_CONFUSIONS = 8;
    static final int CALIBRATION_DAYS = 30;
    /** Đuôi okurigana của cách viết: 見る -> 見 (cột character của từ chữ Hán đơn chỉ có chữ Hán). */
    private static final Pattern TRAILING_HIRAGANA = Pattern.compile("\\p{IsHiragana}+$");
    /** Lịch nhiệt: 12 tuần gần nhất. */
    static final int CALENDAR_DAYS = 84;
    /** Ít lượt ôn hơn thì tỉ lệ nhớ thật dao động quá nhiều để so với dự đoán. */
    static final int MIN_CALIBRATION_REVIEWS = 20;

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

        Map<LocalDate, Long> answersPerDay = answersPerDay(userId);
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
                .calibration(calibration(userId))
                .streak(StudyStreaks.of(answersPerDay.keySet(), today))
                .calendar(calendar(answersPerDay, today))
                .build();
    }

    /** Chỉ chuỗi ngày học - cho thanh bên, gọn hơn cả trang Tiến bộ. */
    @Transactional(readOnly = true)
    public StreakResponse streak(Long userId) {
        return StudyStreaks.of(answersPerDay(userId).keySet(), calendar.today());
    }

    /** Số lần trả lời theo từng ngày học, mọi lúc. */
    private Map<LocalDate, Long> answersPerDay(Long userId) {
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (ReviewLogRepository.HourlyAnswers hour : reviewLogRepository.answersPerHour(userId)) {
            perDay.merge(calendar.dayOf(hour.getHour()), hour.getAnswers(), Long::sum);
        }
        return perDay;
    }

    private static List<ProgressResponse.CalendarDay> calendar(Map<LocalDate, Long> answersPerDay, LocalDate today) {
        LocalDate first = today.minusDays(CALENDAR_DAYS - 1);
        List<ProgressResponse.CalendarDay> days = new ArrayList<>(CALENDAR_DAYS);
        for (int i = 0; i < CALENDAR_DAYS; i++) {
            LocalDate day = first.plusDays(i);
            days.add(new ProgressResponse.CalendarDay(day, answersPerDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private ProgressResponse.Calibration calibration(Long userId) {
        ReviewLogRepository.Calibration row = reviewLogRepository.calibration(userId,
                calendar.now().minusDays(CALIBRATION_DAYS));
        if (row.getReviews() < MIN_CALIBRATION_REVIEWS) {
            return null;
        }
        return new ProgressResponse.Calibration(row.getReviews(), row.getPredicted(), row.getActual());
    }

    private List<ProgressResponse.Confusion> confusions(Long userId) {
        List<QuizMistake> mistakes = reviewLogRepository.topQuizMistakes(userId,
                calendar.now().minusDays(CONFUSION_DAYS), MAX_CONFUSIONS);
        Map<Long, Kanji> words = kanjiRepository.findAllById(mistakes.stream().map(QuizMistake::getKanjiId).toList())
                .stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        Map<QuizMistake, Long> chosenWords = chosenWords(mistakes);
        List<ProgressResponse.Confusion> result = new ArrayList<>();
        for (QuizMistake mistake : mistakes) {
            Kanji word = words.get(mistake.getKanjiId());
            if (word != null) {
                result.add(new ProgressResponse.Confusion(word.getId(), word.getCharacter(), word.getReading(),
                        word.getMeaning(), mistake.getDirection(), mistake.getChosenAnswer(), mistake.getTimes(),
                        chosenWords.get(mistake)));
            }
        }
        return result;
    }

    /**
     * Từ trong kho của đáp án chọn nhầm, để luyện cặp hay nhầm cùng nhau: cách viết (chọn cách viết, điền từ), cách đọc
     * (chọn / gõ cách đọc) hay nghĩa (chọn nghĩa, nghe). Chỉ nhận khi đúng một từ khớp và không phải chính từ được hỏi;
     * mỗi loại một truy vấn.
     */
    private Map<QuizMistake, Long> chosenWords(List<QuizMistake> mistakes) {
        Map<String, List<Kanji>> bySpelling = new HashMap<>();
        Map<String, List<Kanji>> byReading = new HashMap<>();
        Map<String, List<Kanji>> byMeaning = new HashMap<>();
        Set<String> spellings = new HashSet<>();
        Set<String> readings = new HashSet<>();
        Set<String> meanings = new HashSet<>();
        for (QuizMistake mistake : mistakes) {
            String chosen = mistake.getChosenAnswer();
            switch (mistake.getDirection()) {
                case READING_TO_KANJI, CLOZE -> {
                    spellings.add(chosen);
                    spellings.add(TRAILING_HIRAGANA.matcher(chosen).replaceFirst(""));
                }
                case KANJI_TO_READING, TYPE_READING -> readings.add(chosen);
                case MEANING, LISTENING -> meanings.add(chosen);
                case RECALL_WORD -> {
                }
            }
        }
        spellings.remove("");
        if (!spellings.isEmpty()) {
            kanjiRepository.findAllByCharacterIn(spellings).forEach(word -> bySpelling
                    .computeIfAbsent(ReadingMatcher.writtenForm(word.getCharacter(), word.getReading()),
                            key -> new ArrayList<>()).add(word));
        }
        if (!readings.isEmpty()) {
            kanjiRepository.findAllByDisplayReadingIn(readings).forEach(word -> byReading
                    .computeIfAbsent(ReadingMatcher.displayReading(word.getReading()), key -> new ArrayList<>())
                    .add(word));
        }
        if (!meanings.isEmpty()) {
            kanjiRepository.findAllByMeaningIn(meanings).forEach(word -> byMeaning
                    .computeIfAbsent(word.getMeaning(), key -> new ArrayList<>()).add(word));
        }
        Map<QuizMistake, Long> chosenWords = new HashMap<>();
        for (QuizMistake mistake : mistakes) {
            Map<String, List<Kanji>> index = switch (mistake.getDirection()) {
                case READING_TO_KANJI, CLOZE -> bySpelling;
                case KANJI_TO_READING, TYPE_READING -> byReading;
                case MEANING, LISTENING -> byMeaning;
                case RECALL_WORD -> Map.of();
            };
            List<Long> ids = index.getOrDefault(mistake.getChosenAnswer(), List.of()).stream()
                    .map(Kanji::getId)
                    .filter(id -> !id.equals(mistake.getKanjiId()))
                    .distinct()
                    .toList();
            if (ids.size() == 1) {
                chosenWords.put(mistake, ids.get(0));
            }
        }
        return chosenWords;
    }
}
