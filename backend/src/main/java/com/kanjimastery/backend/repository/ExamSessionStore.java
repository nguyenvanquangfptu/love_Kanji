package com.kanjimastery.backend.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.kanjimastery.backend.config.ExamProperties;

/**
 * Bọc toàn bộ thao tác Redis cho phiên làm bài:
 * - {@code exam:session:{attemptId}}  Hash chứa đáp án đã tick (questionId -> option).
 *   TTL = duration + buffer, KHÔNG trùng mốc hết giờ, để tránh mất dữ liệu nếu
 *   event/job xử lý trễ hơn dự kiến.
 * - {@code exam:questions:{attemptId}} danh sách câu hỏi đã gán cho attempt (để
 *   chấm đúng cả những câu bỏ trống), cùng TTL với Hash ở trên.
 * - {@code exam:timeout:{attemptId}}  marker key rỗng, TTL đúng bằng thời gian
 *   thi, chỉ dùng để bắn Keyspace Notification khi hết giờ.
 */
@Component
@RequiredArgsConstructor
public class ExamSessionStore {

    private final StringRedisTemplate redisTemplate;
    private final ExamProperties examProperties;

    public void initSession(Long attemptId, List<Long> questionIds) {
        Duration timeoutTtl = Duration.ofSeconds(examProperties.getDurationSeconds());
        String joinedIds = questionIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        redisTemplate.opsForValue().set(questionsKey(attemptId), joinedIds, sessionTtl());
        redisTemplate.opsForValue().set(timeoutKey(attemptId), "", timeoutTtl);
    }

    public void saveAnswer(Long attemptId, Long questionId, String selectedOption) {
        String key = sessionKey(attemptId);
        redisTemplate.opsForHash().put(key, String.valueOf(questionId), selectedOption);
        redisTemplate.expire(key, sessionTtl());
    }

    public Map<Long, String> getAnswers(Long attemptId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(sessionKey(attemptId));
        Map<Long, String> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(Long.valueOf(k.toString()), v.toString()));
        return result;
    }

    public List<Long> getQuestionIds(Long attemptId) {
        String joined = redisTemplate.opsForValue().get(questionsKey(attemptId));
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(Long::valueOf).toList();
    }

    /** Dọn dữ liệu Redis sau khi đã chấm điểm và lưu DB thành công. */
    public void cleanup(Long attemptId) {
        redisTemplate.delete(sessionKey(attemptId));
        redisTemplate.delete(questionsKey(attemptId));
        redisTemplate.delete(timeoutKey(attemptId));
    }

    private Duration sessionTtl() {
        return Duration.ofSeconds((long) examProperties.getDurationSeconds() + examProperties.getSessionBufferSeconds());
    }

    private String sessionKey(Long attemptId) {
        return "exam:session:" + attemptId;
    }

    private String questionsKey(Long attemptId) {
        return "exam:questions:" + attemptId;
    }

    private String timeoutKey(Long attemptId) {
        return "exam:timeout:" + attemptId;
    }
}
