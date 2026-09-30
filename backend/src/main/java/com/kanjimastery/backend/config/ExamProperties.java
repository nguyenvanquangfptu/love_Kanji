package com.kanjimastery.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.exam")
@Getter
@Setter
public class ExamProperties {

    /** Thời gian làm bài (giây). Mặc định 30 phút. */
    private int durationSeconds = 1800;

    /** Số câu hỏi mặc định khi không truyền questionCount lúc bắt đầu thi. */
    private int defaultQuestionCount = 20;

    /**
     * Thời gian "dôi ra" (giây) cho TTL của Redis Hash chứa đáp án, so với
     * đúng thời gian thi - để tránh mất dữ liệu nếu event/job xử lý trễ.
     */
    private int sessionBufferSeconds = 1800;

    /** Chu kỳ quét của Reconciliation Job (mili-giây). */
    private long reconciliationIntervalMs = 90_000;
}
