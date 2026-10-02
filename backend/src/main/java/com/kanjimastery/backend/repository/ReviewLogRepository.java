package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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

    /** Lượt trả lời một từ theo một hướng hỏi ({@code direction} null = thẻ ôn tập). */
    interface WordDirectionStats {
        Long getKanjiId();

        String getDirection();

        long getAnswers();

        long getErrors();

        /** Số lần sai từ {@code since} trở lại đây. */
        long getRecentErrors();
    }

    /** Một dòng cho mỗi cặp (từ, hướng hỏi) đã từng trả lời - một truy vấn cho cả nhóm từ (idx_review_logs_user_kanji). */
    @Query(value = """
            SELECT kanji_id AS "kanjiId", direction AS "direction", COUNT(*) AS "answers",
                   COUNT(*) FILTER (WHERE NOT correct) AS "errors",
                   COUNT(*) FILTER (WHERE NOT correct AND reviewed_at >= :since) AS "recentErrors"
            FROM review_logs
            WHERE user_id = :userId AND kanji_id IN (:kanjiIds)
            GROUP BY kanji_id, direction
            """, nativeQuery = true)
    List<WordDirectionStats> wordDirectionStats(@Param("userId") Long userId,
                                                @Param("kanjiIds") Collection<Long> kanjiIds,
                                                @Param("since") LocalDateTime since);

    interface DirectionStats {
        String getDirection();

        long getAnswers();

        long getErrors();
    }

    /** Số câu trắc nghiệm và số câu sai theo từng hướng hỏi từ {@code since} (idx_review_logs_user_time). */
    @Query(value = """
            SELECT direction AS "direction", COUNT(*) AS "answers", COUNT(*) FILTER (WHERE NOT correct) AS "errors"
            FROM review_logs
            WHERE user_id = :userId AND source = 'QUIZ' AND reviewed_at >= :since
            GROUP BY direction
            """, nativeQuery = true)
    List<DirectionStats> quizDirectionStats(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** Một đáp án sai người học đã chọn khi được hỏi một từ theo một hướng, và số lần chọn. */
    interface QuizMistake {
        Long getKanjiId();

        String getDirection();

        String getChosenAnswer();

        long getTimes();
    }

    /** Đáp án sai đã chọn trong trắc nghiệm, chọn nhiều lần nhất (rồi gần đây nhất) trước (idx_review_logs_user_kanji). */
    @Query(value = """
            SELECT kanji_id AS "kanjiId", direction AS "direction", chosen_answer AS "chosenAnswer", COUNT(*) AS "times"
            FROM review_logs
            WHERE user_id = :userId AND kanji_id IN (:kanjiIds) AND source = 'QUIZ' AND NOT correct
              AND chosen_answer IS NOT NULL
            GROUP BY kanji_id, direction, chosen_answer
            ORDER BY COUNT(*) DESC, MAX(reviewed_at) DESC
            """, nativeQuery = true)
    List<QuizMistake> quizMistakes(@Param("userId") Long userId, @Param("kanjiIds") Collection<Long> kanjiIds);
}
