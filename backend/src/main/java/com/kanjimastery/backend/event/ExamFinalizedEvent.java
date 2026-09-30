package com.kanjimastery.backend.event;

import com.kanjimastery.backend.listener.ExamFinalizedEventListener;
import com.kanjimastery.backend.service.ExamFinalizationService;

/**
 * Publish sau khi (và chỉ khi) transaction DB của {@link ExamFinalizationService#finalize}
 * commit thành công. Listener ở {@link ExamFinalizedEventListener} lắng nghe event này
 * bằng {@code @TransactionalEventListener(AFTER_COMMIT)} để đẩy điểm lên Redis ZSET và
 * dọn Redis Hash - tách hẳn khỏi transaction DB để tránh dual-write giữa 2 hệ thống.
 */
public record ExamFinalizedEvent(Long attemptId, Long userId, String jlptLevel, int totalScore, int timeSpentSeconds) {
}
