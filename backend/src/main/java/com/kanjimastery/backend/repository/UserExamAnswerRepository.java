package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.UserExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserExamAnswerRepository extends JpaRepository<UserExamAnswer, Long> {

    List<UserExamAnswer> findByAttemptId(Long attemptId);

    /** Theo thứ tự câu hỏi trong bài (câu trả lời được lưu theo thứ tự đó lúc chốt điểm). */
    List<UserExamAnswer> findByAttemptIdOrderByIdAsc(Long attemptId);

    /** Số câu của bài thi đã chốt điểm - mỗi câu một dòng, kể cả câu bỏ trống. */
    long countByAttemptId(Long attemptId);

    /** Người học đã gặp câu này trong một bài thi đã chốt điểm (kể cả khi bỏ trống). */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM user_exam_answers answer
                JOIN user_exam_attempts attempt ON attempt.id = answer.attempt_id
                WHERE attempt.user_id = :userId AND answer.question_id = :questionId
                  AND attempt.status <> 'IN_PROGRESS')
            """, nativeQuery = true)
    boolean existsInFinishedAttemptOf(@Param("userId") Long userId, @Param("questionId") Long questionId);
}
