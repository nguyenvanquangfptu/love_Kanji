package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.ExamSitting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExamSittingRepository extends JpaRepository<ExamSitting, Long> {

    List<ExamSitting> findByStatusAndStartedAtBefore(String status, LocalDateTime startedBefore);

    /** Đọc và khoá buổi thi tới hết transaction - hai yêu cầu cùng lúc không bắt đầu một phần hai lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSitting s WHERE s.id = :id")
    Optional<ExamSitting> findByIdForUpdate(@Param("id") Long id);

    /** Kết thúc buổi thi nếu còn đang làm; trả 0 nếu buổi thi đã kết thúc trước đó. */
    @Modifying
    @Query("""
            UPDATE ExamSitting s SET s.status = :status, s.finishedAt = :finishedAt
            WHERE s.id = :id AND s.status = 'IN_PROGRESS'
            """)
    int finishIfInProgress(@Param("id") Long id, @Param("status") String status,
                           @Param("finishedAt") LocalDateTime finishedAt);
}
