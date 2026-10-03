package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.GrammarPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GrammarPointRepository extends JpaRepository<GrammarPoint, Long> {

    /** Theo bài rồi theo thứ tự nhập. */
    List<GrammarPoint> findByJlptLevelOrderByLessonAscIdAsc(String jlptLevel);

    List<GrammarPoint> findAllByOrderByJlptLevelDescLessonAscIdAsc();

    Optional<GrammarPoint> findByJlptLevelAndPattern(String jlptLevel, String pattern);

    /** Số câu thi gắn với mỗi điểm ngữ pháp, theo trạng thái duyệt. */
    interface QuestionCount {
        Long getGrammarPointId();

        String getStatus();

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
}
