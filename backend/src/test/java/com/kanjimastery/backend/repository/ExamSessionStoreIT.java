package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh bất biến thiết kế: TTL của Redis Hash chứa đáp án
 * ({@code exam:session:*} và {@code exam:questions:*}) LUÔN dôi ra so với TTL
 * của key marker hết giờ ({@code exam:timeout:*}), để tránh mất dữ liệu bài
 * làm nếu event/job xử lý trễ hơn dự kiến.
 */
class ExamSessionStoreIT extends AbstractIntegrationTest {

    @Autowired
    private ExamSessionStore examSessionStore;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long attemptId;

    @AfterEach
    void tearDown() {
        if (attemptId != null) {
            examSessionStore.cleanup(attemptId);
        }
    }

    @Test
    void sessionAndQuestionsKeys_shouldHaveLongerTtlThanTimeoutMarker_whichFollowsTheAttemptsOwnDuration() {
        attemptId = System.nanoTime();
        // Một phần đề JLPT 10 phút, ngắn hơn 30 phút mặc định của thi nhanh.
        examSessionStore.initSession(attemptId, List.of(1L, 2L, 3L), 600);
        examSessionStore.saveAnswer(attemptId, 1L, "A", 600);

        Long sessionTtl = redisTemplate.getExpire("exam:session:" + attemptId);
        Long questionsTtl = redisTemplate.getExpire("exam:questions:" + attemptId);
        Long timeoutTtl = redisTemplate.getExpire("exam:timeout:" + attemptId);

        assertThat(sessionTtl).isNotNull().isPositive();
        assertThat(questionsTtl).isNotNull().isPositive();
        assertThat(timeoutTtl).isNotNull().isPositive();

        // Bất biến cốt lõi: Hash dữ liệu phải "sống" lâu hơn mốc hết giờ,
        // không được đặt trùng TTL - đây chính là điểm đã sửa sau khi review thiết kế.
        assertThat(sessionTtl).isGreaterThan(timeoutTtl);
        assertThat(questionsTtl).isGreaterThan(timeoutTtl);
        assertThat(timeoutTtl).isBetween(590L, 600L);
        assertThat(sessionTtl).isBetween(590L + 1800, 600L + 1800);
    }
}
