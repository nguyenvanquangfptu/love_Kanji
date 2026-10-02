package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Suy mức độ nhớ (Khó / Nhớ / Dễ) của một câu trắc nghiệm trả lời đúng từ thời gian trả lời, so với
 * chính người học đó chứ không theo một ngưỡng chung: người đọc chậm vẫn được "Dễ" khi nhanh hơn hẳn thường lệ.
 */
@Service
@RequiredArgsConstructor
public class ResponseTimeRater {

    /** Quá lâu thì coi như người học đã rời máy - không dùng thời gian này. */
    static final int MAX_RESPONSE_MS = 60_000;
    static final int RECENT_SAMPLES = 200;
    /** Ít câu đúng hơn chừng này thì trung vị chưa đáng tin - dùng ngưỡng cố định. */
    static final int MIN_SAMPLES = 30;
    static final int DEFAULT_EASY_MS = 3_000;
    static final int DEFAULT_HARD_MS = 10_000;
    static final double EASY_FACTOR = 0.6;
    static final double HARD_FACTOR = 1.5;

    private final ReviewLogRepository reviewLogRepository;

    /** Thời gian trả lời dùng được, hoặc null nếu không đo được / không hợp lý. */
    public static Integer normalize(Integer responseMs) {
        if (responseMs == null || responseMs <= 0 || responseMs > MAX_RESPONSE_MS) {
            return null;
        }
        return responseMs;
    }

    /** Không có thời gian trả lời thì coi là "Nhớ". */
    public int rateCorrectAnswer(Long userId, String direction, Integer responseMs) {
        if (responseMs == null) {
            return ReviewRating.GOOD;
        }
        ResponseTimeStats stats = reviewLogRepository.correctQuizResponseTimes(userId, direction, RECENT_SAMPLES);
        double easyUpTo = DEFAULT_EASY_MS;
        double hardAbove = DEFAULT_HARD_MS;
        if (stats != null && stats.getMedianMs() != null && stats.getSamples() >= MIN_SAMPLES) {
            easyUpTo = stats.getMedianMs() * EASY_FACTOR;
            hardAbove = stats.getMedianMs() * HARD_FACTOR;
        }
        if (responseMs <= easyUpTo) {
            return ReviewRating.EASY;
        }
        return responseMs > hardAbove ? ReviewRating.HARD : ReviewRating.GOOD;
    }
}
