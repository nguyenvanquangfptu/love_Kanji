package com.kanjimastery.backend.listener;

import com.kanjimastery.backend.service.ExamDiagnosisService;
import com.kanjimastery.backend.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.kanjimastery.backend.event.ExamFinalizedEvent;
import com.kanjimastery.backend.repository.ExamSessionStore;

/**
 * Chỉ chạy SAU KHI transaction DB của ExamFinalization.finalize() commit
 * thành công (AFTER_COMMIT) - đảm bảo Redis (leaderboard, dọn session)
 * không bao giờ được cập nhật cho một attempt mà DB thực ra chưa/không lưu.
 * Kết quả từng câu cũng được đưa vào ôn tập ở đây, trong transaction riêng
 * ({@link ExamDiagnosisService}); lỗi ở bước này không chặn phần Redis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamFinalizedEventListener {

    private final LeaderboardService leaderboardService;
    private final ExamSessionStore examSessionStore;
    private final ExamDiagnosisService examDiagnosisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExamFinalized(ExamFinalizedEvent event) {
        try {
            examDiagnosisService.diagnose(event.attemptId());
        } catch (Exception e) {
            log.error("Không đưa được kết quả bài thi {} vào ôn tập.", event.attemptId(), e);
        }
        leaderboardService.pushScore(event.jlptLevel(), event.userId(), event.totalScore(), event.timeSpentSeconds());
        examSessionStore.cleanup(event.attemptId());
    }
}
