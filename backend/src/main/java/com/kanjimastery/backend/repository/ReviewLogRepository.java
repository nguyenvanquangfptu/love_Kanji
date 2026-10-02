package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * Nhịp ôn thẻ thật của người học: trung vị khoảng cách giữa hai lần chấm thẻ liền nhau trong {@code limit} lần chấm
     * gần nhất, bỏ các khoảng quá 2 phút (nghỉ giữa chừng hoặc sang phiên khác). Gồm cả thời gian đọc đáp án và chấm.
     */
    @Query(value = """
            SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY gaps.gap_ms) AS "medianMs", COUNT(*) AS "samples"
            FROM (SELECT EXTRACT(EPOCH FROM (recent.reviewed_at - LAG(recent.reviewed_at) OVER (ORDER BY recent.reviewed_at)))
                             * 1000 AS gap_ms
                  FROM (SELECT reviewed_at FROM review_logs
                        WHERE user_id = :userId AND source = 'FLASHCARD'
                        ORDER BY reviewed_at DESC
                        LIMIT :limit) recent) gaps
            WHERE gaps.gap_ms > 0 AND gaps.gap_ms <= 120000
            """, nativeQuery = true)
    ResponseTimeStats flashcardPace(@Param("userId") Long userId, @Param("limit") int limit);

    @Query("SELECT MIN(r.reviewedAt) FROM ReviewLog r WHERE r.userId = :userId")
    Optional<LocalDateTime> firstReviewAt(@Param("userId") Long userId);

    /** Số từ mới (lần đầu được tính vào lịch ôn) người học đã học từ {@code since}. */
    @Query(value = """
            SELECT COUNT(DISTINCT kanji_id) FROM review_logs
            WHERE user_id = :userId AND state_before = 'NEW' AND scheduled AND reviewed_at >= :since
            """, nativeQuery = true)
    long countNewWordsLearnedSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

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

    /** Những đáp án sai người học chọn nhiều lần nhất trên mọi từ, từ {@code since}. */
    @Query(value = """
            SELECT kanji_id AS "kanjiId", direction AS "direction", chosen_answer AS "chosenAnswer", COUNT(*) AS "times"
            FROM review_logs
            WHERE user_id = :userId AND source = 'QUIZ' AND NOT correct AND chosen_answer IS NOT NULL
              AND reviewed_at >= :since
            GROUP BY kanji_id, direction, chosen_answer
            ORDER BY COUNT(*) DESC, MAX(reviewed_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<QuizMistake> topQuizMistakes(@Param("userId") Long userId,
                                      @Param("since") LocalDateTime since,
                                      @Param("limit") int limit);

    /** Một lần trả lời, đủ để thống kê theo ngày/tuần. */
    interface Activity {
        LocalDateTime getReviewedAt();

        String getStateBefore();

        Boolean getScheduled();

        Boolean getCorrect();
    }

    /**
     * Các lần trả lời từ {@code since} - gom theo ngày học ở tầng Java (StudyCalendar) để ngày học tính theo giờ
     * Việt Nam bất kể máy chủ chạy múi giờ nào.
     */
    @Query("""
            SELECT r.reviewedAt AS reviewedAt, r.stateBefore AS stateBefore, r.scheduled AS scheduled, r.correct AS correct
            FROM ReviewLog r
            WHERE r.userId = :userId AND r.reviewedAt >= :since
            """)
    List<Activity> activitySince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** Lần học đầu của một từ và lần ôn kế tiếp. */
    interface FirstReviewOutcome {
        Short getRating();

        LocalDateTime getFirstAt();

        LocalDateTime getNextAt();

        Boolean getRecalled();
    }

    /**
     * Với mỗi từ người học đã học (lần trả lời tính lịch đầu tiên là từ mới) và đã ôn lại ít nhất một lần: mức chấm
     * lần đầu, lúc học, lúc ôn kế tiếp và lần đó còn nhớ không - dữ liệu để tối ưu độ ổn định ban đầu của FSRS.
     */
    @Query(value = """
            SELECT reviews.rating AS "rating", reviews.reviewed_at AS "firstAt", reviews.next_at AS "nextAt",
                   reviews.next_correct AS "recalled"
            FROM (SELECT rating, reviewed_at, state_before,
                         ROW_NUMBER() OVER card_reviews AS position,
                         LEAD(reviewed_at) OVER card_reviews AS next_at,
                         LEAD(correct) OVER card_reviews AS next_correct
                  FROM review_logs
                  WHERE user_id = :userId AND scheduled
                  WINDOW card_reviews AS (PARTITION BY kanji_id ORDER BY reviewed_at, id)) reviews
            WHERE reviews.position = 1 AND reviews.state_before = 'NEW' AND reviews.next_at IS NOT NULL
            """, nativeQuery = true)
    List<FirstReviewOutcome> firstReviewOutcomes(@Param("userId") Long userId);

    /** FSRS dự đoán so với thực tế trên một nhóm lần ôn. */
    interface Calibration {
        long getReviews();

        /** Xác suất nhớ FSRS dự đoán, trung bình; null nếu không có lần ôn nào. */
        Double getPredicted();

        /** Tỉ lệ thật sự nhớ được; null nếu không có lần ôn nào. */
        Double getActual();
    }

    /**
     * Các lần ôn tính lịch từ {@code since} có dự đoán của FSRS, trừ lần ôn lại trong cùng ngày học (khoảng cách 0 ngày
     * thì xác suất ghi là 1 - FSRS chỉ dự đoán cho khoảng cách từ một ngày trở lên).
     */
    @Query(value = """
            SELECT COUNT(*) AS "reviews", AVG(retrievability) AS "predicted",
                   AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END) AS "actual"
            FROM review_logs
            WHERE user_id = :userId AND scheduled AND retrievability < 1 AND reviewed_at >= :since
            """, nativeQuery = true)
    Calibration calibration(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT r.userId FROM ReviewLog r WHERE r.reviewedAt >= :since")
    List<Long> userIdsActiveSince(@Param("since") LocalDateTime since);
}
