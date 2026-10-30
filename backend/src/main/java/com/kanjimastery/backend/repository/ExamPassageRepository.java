package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamPassageRepository extends JpaRepository<ExamPassage, Long>, JpaSpecificationExecutor<ExamPassage> {

    /**
     * Đoạn văn đã duyệt của một phần thi ({@code section}: GRAMMAR, READING) có câu hỏi dạng {@code type}, cho một
     * người học: đoạn chưa gặp đứng trước (ngẫu nhiên), rồi tới đoạn gặp lâu nhất. {@code source} null = mọi nguồn,
     * không thì chỉ đoạn của nguồn đó.
     */
    @Query(value = """
            SELECT p.* FROM exam_passages p
            LEFT JOIN (
                SELECT question.passage_id, MAX(attempt.started_at) AS seen_at
                FROM user_exam_answers answer
                JOIN user_exam_attempts attempt ON attempt.id = answer.attempt_id
                JOIN exam_questions question ON question.id = answer.question_id
                WHERE attempt.user_id = :userId AND question.passage_id IS NOT NULL
                GROUP BY question.passage_id
            ) seen ON seen.passage_id = p.id
            WHERE p.jlpt_level = :level AND p.section = :section AND p.status = 'APPROVED'
              AND (CAST(:source AS VARCHAR) IS NULL OR p.source = CAST(:source AS VARCHAR))
              AND EXISTS (SELECT 1 FROM exam_questions q WHERE q.passage_id = p.id AND q.question_type = :type)
            ORDER BY seen.seen_at NULLS FIRST, RANDOM()
            LIMIT :count
            """, nativeQuery = true)
    List<ExamPassage> findApprovedForLearner(@Param("userId") Long userId, @Param("level") String level,
                                             @Param("section") String section, @Param("type") String type,
                                             @Param("source") String source, @Param("count") int count);

    /** Số bài đọc đã duyệt có cùng dạng câu và cùng số câu hỏi đã duyệt. */
    interface PassageCount {
        JlptQuestionType getType();

        Integer getQuestions();

        Long getPassages();
    }

    /**
     * Đoạn văn đã duyệt của một phần thi, đếm theo dạng câu và số câu hỏi đã duyệt của mỗi đoạn - để biết có bao
     * nhiêu bài trọn vẹn cho mỗi dạng. {@code source} null = mọi nguồn.
     */
    @Query(value = """
            SELECT counted.type AS "type", counted.questions AS "questions", COUNT(*) AS "passages"
            FROM (
                SELECT MIN(q.question_type) AS type, CAST(COUNT(*) AS INTEGER) AS questions
                FROM exam_passages p
                JOIN exam_questions q ON q.passage_id = p.id AND q.status = 'APPROVED'
                WHERE p.jlpt_level = :level AND p.section = :section AND p.status = 'APPROVED'
                  AND (CAST(:source AS VARCHAR) IS NULL OR p.source = CAST(:source AS VARCHAR))
                GROUP BY p.id
            ) counted
            GROUP BY counted.type, counted.questions
            """, nativeQuery = true)
    List<PassageCount> countApprovedBySize(@Param("level") String level, @Param("section") String section,
                                           @Param("source") String source);

    Page<ExamPassage> findByJlptLevelOrderByIdDesc(JlptLevel jlptLevel, Pageable pageable);

    boolean existsBySourceRef(String sourceRef);

    boolean existsByJlptLevelAndSectionAndContent(JlptLevel jlptLevel, ExamSection section, String content);
}
