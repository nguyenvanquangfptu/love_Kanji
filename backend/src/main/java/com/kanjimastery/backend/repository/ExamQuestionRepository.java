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

    /** Các câu hỏi kèm luôn từ vựng mỗi câu kiểm tra, trong một truy vấn. */
    @Query("SELECT DISTINCT q FROM ExamQuestion q LEFT JOIN FETCH q.kanjiIds WHERE q.id IN :ids")
    List<ExamQuestion> findAllWithWordsByIdIn(@Param("ids") Collection<Long> ids);
}
