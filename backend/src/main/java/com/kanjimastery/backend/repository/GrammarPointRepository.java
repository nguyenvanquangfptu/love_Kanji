package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GrammarPointRepository extends JpaRepository<GrammarPoint, Long> {

    /** Theo bài rồi theo thứ tự nhập. */
    List<GrammarPoint> findByJlptLevelOrderByLessonAscIdAsc(JlptLevel jlptLevel);

    List<GrammarPoint> findAllByOrderByJlptLevelDescLessonAscIdAsc();

    Optional<GrammarPoint> findByJlptLevelAndPattern(JlptLevel jlptLevel, String pattern);

    /** Số câu thi gắn với mỗi điểm ngữ pháp, theo trạng thái duyệt. */
    interface QuestionCount {
        Long getGrammarPointId();

        ExamQuestionStatus getStatus();

        Long getCount();
    }

    @Query(value = """
            SELECT link.grammar_point_id AS "grammarPointId", question.status AS "status", COUNT(*) AS "count"
            FROM exam_question_grammar link
            JOIN exam_questions question ON question.id = link.question_id
            WHERE link.grammar_point_id IN (:ids)
            GROUP BY link.grammar_point_id, question.status
            """, nativeQuery = true)
    List<QuestionCount> countQuestionsByStatus(@Param("ids") Collection<Long> ids);

    /** Một điểm ngữ pháp người học đã làm sai: số câu sai trên số câu đã trả lời. */
    interface LearnerMistakes {
        Long getId();

        String getPattern();

        String getMeaningVi();

        Long getWrong();

        Long getAnswered();
    }

    /**
     * Các điểm ngữ pháp người học đã làm sai trong các lượt thi đã chốt của một cấp độ, từ {@code since}: sai nhiều
     * nhất trước, rồi tỉ lệ sai cao, rồi lần sai gần nhất. Không tính câu bỏ trống.
     */
    @Query(value = """
            SELECT point.id AS "id", point.pattern AS "pattern", point.meaning_vi AS "meaningVi",
                   COUNT(*) FILTER (WHERE NOT answer.is_correct) AS "wrong", COUNT(*) AS "answered"
            FROM user_exam_answers answer
            JOIN user_exam_attempts attempt ON attempt.id = answer.attempt_id
            JOIN exam_question_grammar link ON link.question_id = answer.question_id
            JOIN grammar_points point ON point.id = link.grammar_point_id
            WHERE attempt.user_id = :userId AND attempt.jlpt_level = :level AND attempt.status <> 'IN_PROGRESS'
              AND attempt.started_at >= :since AND answer.selected_option IS NOT NULL
            GROUP BY point.id, point.pattern, point.meaning_vi
            HAVING COUNT(*) FILTER (WHERE NOT answer.is_correct) > 0
            ORDER BY COUNT(*) FILTER (WHERE NOT answer.is_correct) DESC,
                     COUNT(*) FILTER (WHERE NOT answer.is_correct)::float / COUNT(*) DESC,
                     MAX(attempt.started_at) FILTER (WHERE NOT answer.is_correct) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<LearnerMistakes> findMistakesOfLearner(@Param("userId") Long userId, @Param("level") String level,
                                                @Param("since") LocalDateTime since, @Param("limit") int limit);
}
