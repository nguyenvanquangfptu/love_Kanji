package com.kanjimastery.backend.job;

import com.kanjimastery.backend.service.ItemAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mỗi tuần phân tích câu hỏi từ kết quả thi thật, gắn cờ câu đáng ngờ cho người duyệt ({@link ItemAnalysisService}). */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemAnalysisJob {

    private final ItemAnalysisService itemAnalysisService;

    @Scheduled(cron = "${app.exam.item-analysis-cron}", zone = "${app.srs.day-zone}")
    public void analyzeQuestions() {
        try {
            itemAnalysisService.analyze();
        } catch (Exception e) {
            log.error("Phân tích câu hỏi thất bại - sẽ thử lại lần chạy sau.", e);
        }
    }
}
