package com.kanjimastery.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import com.kanjimastery.backend.model.UserExamAttempt;

public interface UserExamAttemptRepository extends JpaRepository<UserExamAttempt, Long> {

    List<UserExamAttempt> findByStatusAndStartedAtLessThanEqual(String status, LocalDateTime cutoff);

    /**
     * UPDATE có điều kiện (compare-and-swap ở tầng SQL) - chỉ luồng nào khiến
     * số dòng ảnh hưởng > 0 mới được coi là "thắng cuộc đua" chốt điểm. Đây là
     * cơ chế giải quyết Race Condition giữa Submit thủ công / Keyspace Expired
     * Event / Reconciliation Job khi cả 3 có thể cùng cố chốt điểm 1 attempt.
     */
    @Modifying
    @Query("""
            UPDATE UserExamAttempt a
            SET a.status = :status, a.totalScore = :score, a.timeSpentSeconds = :timeSpent, a.submittedAt = :submittedAt
            WHERE a.id = :id AND a.status = 'IN_PROGRESS'
            """)
    int finalizeIfInProgress(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("score") int score,
                              @Param("timeSpent") int timeSpent,
                              @Param("submittedAt") LocalDateTime submittedAt);

    /** Đánh dấu bài thi đã được đưa vào ôn tập; trả 0 nếu bài chưa chốt điểm hoặc đã được đưa vào rồi. */
    @Modifying
    @Query("""
            UPDATE UserExamAttempt a
            SET a.diagnosedAt = :diagnosedAt
            WHERE a.id = :id AND a.diagnosedAt IS NULL AND a.status <> 'IN_PROGRESS'
            """)
    int markDiagnosed(@Param("id") Long id, @Param("diagnosedAt") LocalDateTime diagnosedAt);
}
