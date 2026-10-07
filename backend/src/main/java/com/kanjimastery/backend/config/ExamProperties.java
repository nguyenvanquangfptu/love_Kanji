package com.kanjimastery.backend.config;

import com.kanjimastery.backend.model.UserExamAttempt;
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

    /** Buổi làm đề JLPT còn dở sau chừng này giờ (không có phần nào đang làm) thì coi như bỏ dở. */
    private int sittingMaxHours = 3;

    /** Chu kỳ quét buổi làm đề JLPT bỏ dở (mili-giây). */
    private long sittingCleanupIntervalMs = 600_000;

    /** Thời gian làm bài của một lượt: riêng của lượt nếu có (các phần đề JLPT), không thì mặc định. */
    public int durationOf(UserExamAttempt attempt) {
        return attempt.getDurationSeconds() != null ? attempt.getDurationSeconds() : durationSeconds;
    }
}
