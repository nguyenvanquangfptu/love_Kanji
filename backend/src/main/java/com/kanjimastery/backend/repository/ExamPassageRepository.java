package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.ExamPassage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamPassageRepository extends JpaRepository<ExamPassage, Long> {

    @Query(value = """
            SELECT * FROM exam_passages WHERE jlpt_level = :level AND status = 'APPROVED' ORDER BY RANDOM() LIMIT :count
            """, nativeQuery = true)
    List<ExamPassage> findRandomApproved(@Param("level") String level, @Param("count") int count);

    Page<ExamPassage> findByJlptLevelOrderByIdDesc(String jlptLevel, Pageable pageable);

    Page<ExamPassage> findByJlptLevelAndStatusOrderByIdDesc(String jlptLevel, String status, Pageable pageable);
}
