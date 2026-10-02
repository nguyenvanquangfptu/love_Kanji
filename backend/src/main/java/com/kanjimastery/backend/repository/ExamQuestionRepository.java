package com.kanjimastery.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import com.kanjimastery.backend.model.ExamQuestion;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    @Query(value = "SELECT * FROM exam_questions WHERE jlpt_level = :level ORDER BY RANDOM() LIMIT :count",
            nativeQuery = true)
    List<ExamQuestion> findRandomByLevel(@Param("level") String level, @Param("count") int count);

    /** Một câu thi đã sinh: kiểm tra kỹ năng nào của từ nào. */
    interface GeneratedQuestionWord {
        Long getKanjiId();

        String getSkill();
    }

    @Query(value = """
            SELECT link.kanji_id AS "kanjiId", question.skill AS "skill"
            FROM exam_questions question
            JOIN exam_question_kanji link ON link.question_id = question.id
            WHERE question.source = 'GENERATED' AND question.jlpt_level = :level
            """, nativeQuery = true)
    List<GeneratedQuestionWord> generatedQuestionWords(@Param("level") String level);

    /** Các câu hỏi kèm luôn từ vựng mỗi câu kiểm tra, trong một truy vấn. */
    @Query("SELECT DISTINCT q FROM ExamQuestion q LEFT JOIN FETCH q.kanjiIds WHERE q.id IN :ids")
    List<ExamQuestion> findAllWithWordsByIdIn(@Param("ids") Collection<Long> ids);
}
