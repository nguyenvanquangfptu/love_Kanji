package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.QuestionReportStatus;
import com.kanjimastery.backend.model.ExamQuestionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamQuestionReportRepository extends JpaRepository<ExamQuestionReport, Long> {

    Optional<ExamQuestionReport> findByQuestionIdAndUserId(Long questionId, Long userId);

    long countByQuestionIdAndStatus(Long questionId, QuestionReportStatus status);

    List<ExamQuestionReport> findByQuestionIdInAndStatusOrderByCreatedAtAsc(Collection<Long> questionIds,
                                                                       QuestionReportStatus status);

    /** Các câu (trong {@code questionIds}) người học đã báo lỗi, ở bất kỳ trạng thái nào. */
    @Query("SELECT r.questionId FROM ExamQuestionReport r WHERE r.userId = :userId AND r.questionId IN :questionIds")
    List<Long> findQuestionIdsReportedBy(@Param("userId") Long userId,
                                         @Param("questionIds") Collection<Long> questionIds);

    /** Đóng các báo lỗi đang mở của các câu: RESOLVED (đã xử lý) hoặc DISMISSED (câu không sai). */
    @Modifying
    @Query("""
            UPDATE ExamQuestionReport r SET r.status = :status, r.resolvedAt = :closedAt
            WHERE r.questionId IN :questionIds AND r.status = com.kanjimastery.backend.model.QuestionReportStatus.OPEN
            """)
    int closeOpen(@Param("questionIds") Collection<Long> questionIds, @Param("status") QuestionReportStatus status,
                  @Param("closedAt") LocalDateTime closedAt);
}
