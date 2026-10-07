package com.kanjimastery.backend.job;

import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.service.FsrsParametersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
    /**
     * Ngưỡng xem xét chia review_logs theo tháng (partition). Dưới ngưỡng, hai index (user_id, reviewed_at) và (user_id,
     * kanji_id, reviewed_at) đủ nhanh; chia sớm chỉ thêm phức tạp.
     */
    static final long PARTITION_ROWS = 50_000_000L;
    static final long PARTITION_BYTES = 10L * 1024 * 1024 * 1024;

    private final ReviewLogRepository reviewLogRepository;
    private final FsrsParametersService fsrsParametersService;
    private final Clock clock;

    @Scheduled(cron = "${app.srs.fsrs-optimize-cron}", zone = "${app.srs.day-zone}")
    public void optimizeActiveLearners() {
        List<Long> userIds = reviewLogRepository.userIdsActiveSince(LocalDateTime.now(clock).minusDays(ACTIVE_DAYS));
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
        reportReviewLogSize();
    }

    /** review_logs lớn nhanh nhất (mỗi câu trả lời một dòng): ghi kích thước mỗi tuần, cảnh báo khi tới ngưỡng. */
    private void reportReviewLogSize() {
        ReviewLogRepository.TableSize size = reviewLogRepository.tableSize();
        long megabytes = size.getBytes() / (1024 * 1024);
        if (needsPartitioning(size.getRows(), size.getBytes())) {
            log.warn("review_logs: khoảng {} dòng, {} MB - đã tới ngưỡng, cân nhắc chia partition theo tháng.",
                    size.getRows(), megabytes);
        } else {
            log.info("review_logs: khoảng {} dòng, {} MB.", size.getRows(), megabytes);
        }
    }

    static boolean needsPartitioning(long rows, long bytes) {
        return rows >= PARTITION_ROWS || bytes >= PARTITION_BYTES;
    }
}
