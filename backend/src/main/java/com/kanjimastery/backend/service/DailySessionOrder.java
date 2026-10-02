package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.UserKanjiSrs;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Thứ tự phiên ôn hôm nay: thẻ ôn có nguy cơ quên cao nhất trước, từ mới (chưa học lần nào) xen vào sau mỗi
 * {@value #REVIEWS_BETWEEN_NEW_WORDS} thẻ ôn để phiên ôn không dồn hết từ mới vào đầu hay cuối.
 */
final class DailySessionOrder {

    static final int REVIEWS_BETWEEN_NEW_WORDS = 4;

    private DailySessionOrder() {
    }

    /** {@code due} là mọi thẻ đến hạn; lấy tối đa {@code maxReviews} thẻ ôn và {@code maxNewWords} từ mới. */
    static List<UserKanjiSrs> order(List<UserKanjiSrs> due, LocalDateTime now, int maxReviews, int maxNewWords) {
        List<UserKanjiSrs> reviews = due.stream()
                .filter(card -> card.getLastReviewedAt() != null)
                .sorted(Comparator.comparingDouble((UserKanjiSrs card) -> overdueRatio(card, now)).reversed()
                        .thenComparing(UserKanjiSrs::getNextReviewAt))
                .limit(Math.max(maxReviews, 0))
                .toList();
        // Từ mới theo thứ tự được thêm vào; cùng một lần thêm (một bài) thì theo thứ tự id từ.
        Iterator<UserKanjiSrs> newWords = due.stream()
                .filter(card -> card.getLastReviewedAt() == null)
                .sorted(Comparator.comparing(UserKanjiSrs::getNextReviewAt).thenComparing(UserKanjiSrs::getKanjiId))
                .limit(Math.max(maxNewWords, 0))
                .iterator();

        List<UserKanjiSrs> session = new ArrayList<>();
        for (int i = 0; i < reviews.size(); i++) {
            session.add(reviews.get(i));
            if ((i + 1) % REVIEWS_BETWEEN_NEW_WORDS == 0 && newWords.hasNext()) {
                session.add(newWords.next());
            }
        }
        newWords.forEachRemaining(session::add);
        return session;
    }

    /**
     * Thẻ đã trễ bao nhiêu khoảng ôn: trễ 2 ngày với khoảng ôn 1 ngày (2,0) dễ quên hơn nhiều so với trễ 2 ngày
     * với khoảng ôn 30 ngày (0,07). Chưa đến hạn thì 0.
     */
    static double overdueRatio(UserKanjiSrs card, LocalDateTime now) {
        double daysLate = Duration.between(card.getNextReviewAt(), now).toMinutes() / 1440.0;
        return Math.max(daysLate, 0) / Math.max(card.getReviewIntervalDays(), 1);
    }
}
