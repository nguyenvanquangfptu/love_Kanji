package com.kanjimastery.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.kanjimastery.backend.model.UserExamAnswer;

public interface UserExamAnswerRepository extends JpaRepository<UserExamAnswer, Long> {

    List<UserExamAnswer> findByAttemptId(Long attemptId);
}
