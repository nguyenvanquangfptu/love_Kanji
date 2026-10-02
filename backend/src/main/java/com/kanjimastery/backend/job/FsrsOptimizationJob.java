package com.kanjimastery.backend.job;

import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.service.FsrsParametersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hằng tuần tối ưu lại tham số FSRS của những người đã ôn trong tuần qua - dữ liệu mới chỉ đến từ các lần ôn mới, nên
 * người không ôn thì không cần tính lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FsrsOptimizationJob {

    private static final int ACTIVE_DAYS = 7;

    private final ReviewLogRepository reviewLogRepository;
    private final FsrsParametersService fsrsParametersService;

    @Scheduled(cron = "${app.srs.fsrs-optimize-cron}", zone = "${app.srs.day-zone}")
    public void optimizeActiveLearners() {
        List<Long> userIds = reviewLogRepository.userIdsActiveSince(LocalDateTime.now().minusDays(ACTIVE_DAYS));
        int personalized = 0;
        for (Long userId : userIds) {
            try {
                if (fsrsParametersService.optimize(userId).isPersonalized()) {
                    personalized++;
                }
            } catch (Exception e) {
                log.error("Tối ưu tham số FSRS cho người dùng {} thất bại - sẽ thử lại tuần sau.", userId, e);
            }
        }
        log.info("Tối ưu tham số FSRS: {} người ôn trong tuần qua, {} người có tham số riêng.", userIds.size(),
                personalized);
    }
}
