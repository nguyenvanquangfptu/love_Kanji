package com.kanjimastery.backend.listener;

import com.kanjimastery.backend.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.kanjimastery.backend.event.ExamFinalizedEvent;
import com.kanjimastery.backend.repository.ExamSessionStore;

/**
 * Chỉ chạy SAU KHI transaction DB của ExamFinalization.finalize() commit
 * thành công (AFTER_COMMIT) - đảm bảo Redis (leaderboard, dọn session)
 * không bao giờ được cập nhật cho một attempt mà DB thực ra chưa/không lưu.
 */
@Component
@RequiredArgsConstructor
public class ExamFinalizedEventListener {

    private final LeaderboardService leaderboardService;
    private final ExamSessionStore examSessionStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExamFinalized(ExamFinalizedEvent event) {
        leaderboardService.pushScore(event.jlptLevel(), event.userId(), event.totalScore(), event.timeSpentSeconds());
        examSessionStore.cleanup(event.attemptId());
    }
}
