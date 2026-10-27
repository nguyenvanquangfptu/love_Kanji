package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.GrammarReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface GrammarReviewLogRepository extends JpaRepository<GrammarReviewLog, Long> {

    /**
     * Số điểm ngữ pháp mới đã học từ một thời điểm (đầu ngày học) - để giới hạn số điểm mới mỗi ngày. Viết JPQL vì tên
     * hàm suy ra truy vấn sẽ đọc "StateBefore" thành từ khoá "Before".
     */
    @Query("""
            SELECT count(l) FROM GrammarReviewLog l
            WHERE l.userId = :userId AND l.stateBefore = com.kanjimastery.backend.model.CardState.NEW
              AND l.reviewedAt >= :from
            """)
    long countNewLearnedSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
