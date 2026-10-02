package com.kanjimastery.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.kanjimastery.backend.model.UserExamAnswer;

public interface UserExamAnswerRepository extends JpaRepository<UserExamAnswer, Long> {

    List<UserExamAnswer> findByAttemptId(Long attemptId);

    /** Theo thứ tự câu hỏi trong bài (câu trả lời được lưu theo thứ tự đó lúc chốt điểm). */
    List<UserExamAnswer> findByAttemptIdOrderByIdAsc(Long attemptId);

    /** Số câu của bài thi đã chốt điểm - mỗi câu một dòng, kể cả câu bỏ trống. */
    long countByAttemptId(Long attemptId);
}
