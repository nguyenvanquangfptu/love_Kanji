package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.ExamQuestionStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamQuestionStatsRepository extends JpaRepository<ExamQuestionStats, Long> {

    /** Thống kê một câu tính từ câu trả lời. */
    interface ComputedStats {
        Long getQuestionId();

        Long getResponses();

        Double getCorrectRate();

        Double getDiscrimination();
    }

    /**
     * Tính thống kê các câu thi JLPT có ít nhất {@code minResponses} lượt trả lời (không tính bỏ trống) trong các bài
     * đã chốt, từ lần duyệt câu gần nhất. Độ phân biệt: so theo kết quả của bài trừ chính câu đó, tỉ lệ đúng của 27%
     * lượt làm tốt nhất trừ 27% lượt làm kém nhất.
     */
    @Query(value = """
            WITH attempt_totals AS (
                SELECT answer.attempt_id, COUNT(*) AS answered,
                       COUNT(*) FILTER (WHERE answer.is_correct) AS correct
                FROM user_exam_answers answer
                JOIN user_exam_attempts attempt ON attempt.id = answer.attempt_id
                WHERE attempt.status <> 'IN_PROGRESS'
                GROUP BY answer.attempt_id
            ),
            responses AS (
                SELECT answer.question_id,
                       CASE WHEN answer.is_correct THEN 1 ELSE 0 END AS is_right,
                       (totals.correct - CASE WHEN answer.is_correct THEN 1 ELSE 0 END)::float
                           / NULLIF(totals.answered - 1, 0) AS rest
                FROM user_exam_answers answer
                JOIN attempt_totals totals ON totals.attempt_id = answer.attempt_id
                JOIN exam_questions question ON question.id = answer.question_id
                WHERE answer.selected_option IS NOT NULL AND question.question_type IS NOT NULL
                  AND (question.reviewed_at IS NULL OR answer.answered_at > question.reviewed_at)
            ),
            ranked AS (
                SELECT question_id, is_right,
                       PERCENT_RANK() OVER (PARTITION BY question_id ORDER BY rest) AS position
                FROM responses
                WHERE rest IS NOT NULL
            )
            SELECT question_id AS "questionId", COUNT(*) AS "responses", AVG(is_right)::float AS "correctRate",
                   (AVG(is_right) FILTER (WHERE position >= 0.73)
                       - AVG(is_right) FILTER (WHERE position <= 0.27))::float AS "discrimination"
            FROM ranked
            GROUP BY question_id
            HAVING COUNT(*) >= :minResponses
            """, nativeQuery = true)
    List<ComputedStats> computeAll(@Param("minResponses") int minResponses);
}
