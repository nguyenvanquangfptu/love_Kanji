package com.kanjimastery.backend.job;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.service.ExamQuestionGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/** Mỗi sáng sinh câu thi cho các từ mới thêm vào bài học (chỉ sinh câu chưa có, nên chạy lại không tốn gì nhiều). */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamQuestionGenerationJob {

    private final ExamQuestionGenerator examQuestionGenerator;

    @Scheduled(cron = "${app.exam.question-generation-cron}", zone = "${app.srs.day-zone}")
    public void generateMissingQuestions() {
        for (JlptLevel level : JlptLevel.values()) {
            try {
                examQuestionGenerator.generate(level.name());
            } catch (Exception e) {
                log.error("Sinh câu thi {} thất bại - sẽ thử lại lần chạy sau.", level, e);
            }
        }
    }
}
