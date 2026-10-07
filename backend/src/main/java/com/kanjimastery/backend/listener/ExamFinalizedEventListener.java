package com.kanjimastery.backend.listener;

import com.kanjimastery.backend.service.ExamDiagnosisService;
import com.kanjimastery.backend.service.JlptExamService;
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
 * Lượt thi là một phần của đề JLPT thì không tính vào bảng xếp hạng thi
 * nhanh; làm xong phần cuối thì buổi thi hoàn thành ({@link JlptExamService}),
 * và buổi thi trọn vẹn lên bảng xếp hạng đề JLPT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamFinalizedEventListener {

    private final LeaderboardService leaderboardService;
    private final ExamSessionStore examSessionStore;
    private final ExamDiagnosisService examDiagnosisService;
    private final JlptExamService jlptExamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExamFinalized(ExamFinalizedEvent event) {
        try {
            examDiagnosisService.diagnose(event.attemptId());
        } catch (Exception e) {
            log.error("Không đưa được kết quả bài thi {} vào ôn tập.", event.attemptId(), e);
        }
        if (event.sittingId() == null) {
            leaderboardService.pushScore(event.jlptLevel(), event.userId(), event.totalScore(), event.timeSpentSeconds());
        } else {
            try {
                jlptExamService.onSectionFinished(event.sittingId()).ifPresent(leaderboardService::pushJlptResult);
            } catch (Exception e) {
                log.error("Không cập nhật được buổi thi {} sau khi chốt lượt thi {} - job dọn dẹp sẽ xử lý bù.",
                        event.sittingId(), event.attemptId(), e);
            }
        }
        examSessionStore.cleanup(event.attemptId());
    }
}
