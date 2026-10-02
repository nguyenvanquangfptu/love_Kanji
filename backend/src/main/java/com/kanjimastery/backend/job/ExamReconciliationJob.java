package com.kanjimastery.backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import com.kanjimastery.backend.config.ExamProperties;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.service.ExamFinalizationService;

/**
 * Lớp bảo vệ DỰ PHÒNG: quét định kỳ các attempt còn IN_PROGRESS nhưng đã quá
 * hạn làm bài, tự chấm điểm bù cho những trường hợp Redis Keyspace Notification
 * bị bỏ lỡ (best-effort, không đảm bảo delivery). Đảm bảo không bài thi nào
 * bị "treo" vĩnh viễn ở trạng thái IN_PROGRESS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamReconciliationJob {

    private final UserExamAttemptRepository attemptRepository;
    private final ExamFinalizationService examFinalizationService;
    private final ExamProperties examProperties;

    @Scheduled(fixedDelayString = "${app.exam.reconciliation-interval-ms:90000}")
    public void reconcileExpiredAttempts() {
        // Mỗi lượt có thời gian làm bài riêng (các phần đề JLPT ngắn hơn thi nhanh): lấy mọi lượt đã quá 1 phút rồi lọc
        // theo thời gian của từng lượt.
        LocalDateTime now = LocalDateTime.now();
        List<UserExamAttempt> staleAttempts = attemptRepository
                .findByStatusAndStartedAtLessThanEqual(ExamAttemptStatus.IN_PROGRESS, now.minusMinutes(1)).stream()
                .filter(attempt -> !attempt.getStartedAt().plusSeconds(examProperties.durationOf(attempt)).isAfter(now))
                .toList();

        if (staleAttempts.isEmpty()) {
            return;
        }

        log.info("Reconciliation Job tìm thấy {} attempt quá hạn chưa được xử lý.", staleAttempts.size());
        for (UserExamAttempt attempt : staleAttempts) {
            try {
                examFinalizationService.finalize(attempt.getId(), ExamAttemptStatus.TIMEOUT);
            } catch (Exception e) {
                log.error("Reconciliation Job lỗi khi finalize attempt {} - sẽ thử lại ở lượt quét sau.",
                        attempt.getId(), e);
            }
        }
    }
}
