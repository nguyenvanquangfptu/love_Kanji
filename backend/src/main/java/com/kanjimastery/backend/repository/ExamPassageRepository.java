package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamPassage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamPassageRepository extends JpaRepository<ExamPassage, Long> {

    /** Đoạn văn đã duyệt cho một người học: đoạn chưa gặp đứng trước (ngẫu nhiên), rồi tới đoạn gặp lâu nhất. */
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
            WHERE p.jlpt_level = :level AND p.status = 'APPROVED'
            ORDER BY seen.seen_at NULLS FIRST, RANDOM()
            LIMIT :count
            """, nativeQuery = true)
    List<ExamPassage> findApprovedForLearner(@Param("userId") Long userId, @Param("level") String level,
                                             @Param("count") int count);

    Page<ExamPassage> findByJlptLevelOrderByIdDesc(JlptLevel jlptLevel, Pageable pageable);

    Page<ExamPassage> findByJlptLevelAndStatusOrderByIdDesc(JlptLevel jlptLevel, ExamQuestionStatus status, Pageable pageable);
}
