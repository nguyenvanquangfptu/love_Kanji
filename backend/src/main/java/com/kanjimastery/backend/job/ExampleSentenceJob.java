package com.kanjimastery.backend.job;

import com.kanjimastery.backend.service.ExampleSentenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi đêm nhờ AI viết câu ví dụ cho các từ còn thiếu, trước khi {@link ExamQuestionGenerationJob} sinh câu thi từ câu
 * ví dụ. Số request có trần riêng ({@code app.ai.sentence-job-requests}) để chừa hạn mức Gemini cho người học.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExampleSentenceJob {

    private final ExampleSentenceService exampleSentenceService;

    @Value("${app.ai.sentence-job-requests:20}")
    private int requestsPerRun;

    @Scheduled(cron = "${app.ai.sentence-job-cron:0 45 4 * * *}", zone = "${app.srs.day-zone}")
    public void fillMissingSentences() {
        try {
            exampleSentenceService.fillMissing(requestsPerRun);
        } catch (Exception e) {
            log.error("AI viết câu ví dụ thất bại - sẽ thử lại lần chạy sau.", e);
        }
    }
}
