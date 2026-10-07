package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.service.LearnerHistory.Tally;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;

/**
 * Chọn từ và hướng hỏi cho trắc nghiệm theo {@link LearnerHistory} thay vì ngẫu nhiên đều. Bài trắc nghiệm gồm
 * khoảng 60% từ đang yếu, 25% từ chưa gặp và 15% từ đã thuộc để kiểm tra lại; nhóm nào thiếu thì lấy bù từ các từ
 * còn lại (từ yếu vẫn nặng ký hơn). Trong mỗi nhóm vẫn bốc ngẫu nhiên nên làm lại không ra y hệt bài cũ.
 * Người chưa có lịch sử thì mọi từ đều "chưa gặp" - giống hệt chọn ngẫu nhiên.
 */
final class AdaptiveQuizPlanner {

    static final double WEAK_SHARE = 0.60;
    static final double NEW_SHARE = 0.25;
    static final BigDecimal LOW_EASINESS = new BigDecimal("2.0");
    /** Trần cho phần trọng số do quá hạn, để một thẻ bỏ quên lâu không lấn át mọi từ khác. */
    static final double MAX_OVERDUE_WEIGHT = 5;
    /** Từ còn ít lượt hỏi theo một hướng thì dựa thêm vào tỉ lệ sai chung của người học, tính như chừng này lượt. */
    static final double DIRECTION_PRIOR = 2;
    /** Hướng ít sai vẫn thỉnh thoảng được hỏi. */
    static final double DIRECTION_FLOOR = 0.1;

    enum Group {
        /** Đến hạn ôn, sai gần đây hoặc độ dễ (EF) thấp. */
        WEAK,
        /** Chưa từng gặp: không có trong lịch ôn và chưa từng trả lời. */
        NEW,
        /** Đã thuộc (khoảng ôn từ 21 ngày), chưa đến hạn. */
        CHECK,
        /** Đã gặp, không yếu cũng chưa thuộc. */
        OTHER
    }

    private AdaptiveQuizPlanner() {
    }

    static Group group(Kanji kanji, LearnerHistory history) {
        Long id = kanji.getId();
        if (!history.seen(id)) {
            return Group.NEW;
        }
        UserKanjiSrs card = history.card(id);
        boolean due = card != null && !card.getNextReviewAt().isAfter(history.now());
        boolean lowEasiness = card != null && card.getEasinessFactor().compareTo(LOW_EASINESS) < 0;
        if (due || lowEasiness || history.recentErrors(id) > 0) {
            return Group.WEAK;
        }
        return card != null && card.getReviewIntervalDays() >= SrsService.MASTERED_INTERVAL_DAYS_THRESHOLD
                ? Group.CHECK
                : Group.OTHER;
    }

    /** {@code 1 + tỉ lệ quá hạn (tối đa 5) + 2 × số lần sai gần đây + 1 nếu EF thấp} cho từ yếu; 1 cho từ khác. */
    static double weight(Kanji kanji, LearnerHistory history) {
        if (group(kanji, history) != Group.WEAK) {
            return 1;
        }
        double weight = 1 + 2.0 * history.recentErrors(kanji.getId());
        UserKanjiSrs card = history.card(kanji.getId());
        if (card != null) {
            weight += Math.min(DailySessionOrder.overdueRatio(card, history.now()), MAX_OVERDUE_WEIGHT);
            if (card.getEasinessFactor().compareTo(LOW_EASINESS) < 0) {
                weight += 1;
            }
        }
        return weight;
    }

    static List<Kanji> selectWords(List<Kanji> pool, int count, LearnerHistory history, RandomGenerator random) {
        int target = Math.min(count, pool.size());
        Map<Group, List<Kanji>> groups = new EnumMap<>(Group.class);
        for (Kanji kanji : pool) {
            groups.computeIfAbsent(group(kanji, history), g -> new ArrayList<>()).add(kanji);
        }

        int weakQuota = (int) Math.round(target * WEAK_SHARE);
        int newQuota = (int) Math.round(target * NEW_SHARE);
        int checkQuota = target - weakQuota - newQuota;
        ToDoubleFunction<Kanji> weight = kanji -> weight(kanji, history);

        List<Kanji> chosen = new ArrayList<>(target);
        chosen.addAll(sample(groups.getOrDefault(Group.WEAK, List.of()), weakQuota, weight, random));
        chosen.addAll(sample(groups.getOrDefault(Group.NEW, List.of()), newQuota, kanji -> 1, random));
        chosen.addAll(sample(groups.getOrDefault(Group.CHECK, List.of()), checkQuota, kanji -> 1, random));
        if (chosen.size() < target) {
            Set<Long> taken = new HashSet<>();
            chosen.forEach(kanji -> taken.add(kanji.getId()));
            List<Kanji> rest = pool.stream().filter(kanji -> !taken.contains(kanji.getId())).toList();
            chosen.addAll(sample(rest, target - chosen.size(), weight, random));
        }
        Collections.shuffle(chosen, random);
        return chosen;
    }

    /**
     * Hướng hỏi cho từ có cách đọc, xác suất tỉ lệ với độ yếu của người học ở mỗi hướng với chính từ đó:
     * {@code (số lần sai + 2 × tỉ lệ sai chung theo hướng đó) / (số lần hỏi + 2) + 0,1}. Chưa có lịch sử thì 50/50.
     */
    static QuizDirection chooseDirection(Kanji kanji, LearnerHistory history, RandomGenerator random) {
        double toReading = weakness(kanji.getId(), KANJI_TO_READING, history);
        double toKanji = weakness(kanji.getId(), READING_TO_KANJI, history);
        return random.nextDouble() * (toReading + toKanji) < toReading ? KANJI_TO_READING : READING_TO_KANJI;
    }

    static double weakness(Long kanjiId, QuizDirection direction, LearnerHistory history) {
        Tally learner = history.directionTally(direction);
        double learnerErrorRate = (learner.errors() + 1.0) / (learner.answers() + 2.0);
        Tally word = history.tally(kanjiId, direction);
        return (word.errors() + DIRECTION_PRIOR * learnerErrorRate) / (word.answers() + DIRECTION_PRIOR) + DIRECTION_FLOOR;
    }

    /**
     * Bốc {@code k} phần tử không lặp lại, phần tử nặng ký hơn dễ được chọn hơn (Efraimidis-Spirakis:
     * mỗi phần tử nhận khoá {@code ln(u) / trọng số} với u ngẫu nhiên trong (0, 1], lấy k khoá lớn nhất).
     */
    static <T> List<T> sample(List<T> items, int k, ToDoubleFunction<T> weight, RandomGenerator random) {
        if (k <= 0 || items.isEmpty()) {
            return List.of();
        }
        double[] keys = new double[items.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = Math.log(1 - random.nextDouble()) / weight.applyAsDouble(items.get(i));
        }
        return IntStream.range(0, keys.length)
                .boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> keys[i]).reversed())
                .limit(k)
                .map(items::get)
                .toList();
    }
}
