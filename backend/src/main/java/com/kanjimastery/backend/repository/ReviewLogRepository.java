package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    /** Trung vị thời gian trả lời và số mẫu - {@code medianMs} là null khi chưa có mẫu nào. */
    interface ResponseTimeStats {
        Double getMedianMs();

        long getSamples();
    }

    /**
     * Thời gian trả lời của {@code limit} câu trắc nghiệm trả lời đúng gần nhất theo một hướng hỏi
     * (đi theo index idx_review_logs_user_time).
     */
    @Query(value = """
            SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY recent.response_ms) AS "medianMs",
                   COUNT(*) AS "samples"
            FROM (SELECT response_ms FROM review_logs
                  WHERE user_id = :userId AND source = 'QUIZ' AND direction = :direction
                    AND correct AND response_ms IS NOT NULL
                  ORDER BY reviewed_at DESC
                  LIMIT :limit) recent
            """, nativeQuery = true)
    ResponseTimeStats correctQuizResponseTimes(@Param("userId") Long userId,
                                               @Param("direction") String direction,
                                               @Param("limit") int limit);
}
