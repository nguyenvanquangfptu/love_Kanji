package com.kanjimastery.backend.job;

import com.kanjimastery.backend.config.ExamProperties;
import com.kanjimastery.backend.model.ExamSitting;
import com.kanjimastery.backend.model.ExamSittingStatus;
import com.kanjimastery.backend.repository.ExamSittingRepository;
import com.kanjimastery.backend.service.JlptExamService;
import com.kanjimastery.backend.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Kết thúc các buổi làm đề JLPT bắt đầu đã quá {@code app.exam.sitting-max-hours} giờ mà chưa làm hết các phần: người
 * học bỏ giữa chừng thì buổi thi thành bỏ dở (giữ kết quả các phần đã làm); cũng là lớp dự phòng khi bước hoàn thành
 * buổi thi sau khi nộp phần cuối bị lỗi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamSittingCleanupJob {

    private final ExamSittingRepository sittingRepository;
    private final JlptExamService jlptExamService;
    private final LeaderboardService leaderboardService;
    private final ExamProperties examProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.exam.sitting-cleanup-interval-ms:600000}")
    public void closeStaleSittings() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(examProperties.getSittingMaxHours());
        for (ExamSitting sitting : sittingRepository.findByStatusAndStartedAtBefore(ExamSittingStatus.IN_PROGRESS,
                cutoff)) {
            try {
                jlptExamService.closeStale(sitting.getId()).ifPresent(leaderboardService::pushJlptResult);
            } catch (Exception e) {
                log.error("Không kết thúc được buổi thi {} - sẽ thử lại ở lượt quét sau.", sitting.getId(), e);
            }
        }
    }
}
