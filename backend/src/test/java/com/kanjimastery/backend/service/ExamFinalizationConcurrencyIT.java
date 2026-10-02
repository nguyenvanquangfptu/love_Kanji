package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;

/**
 * Integration test THẬT (không mock) chứng minh cơ chế CAS trong
 * {@link ExamFinalizationService#finalize} giải quyết đúng Race Condition
 * giữa nhiều luồng cùng cố chốt điểm 1 attempt. Chạy trên Postgres + Redis
 * thật trong Testcontainers (xem {@link AbstractIntegrationTest}) - không
 * phụ thuộc docker-compose của môi trường local, chạy được cả trong CI.
 */
class ExamFinalizationConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private ExamFinalizationService examFinalizationService;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private UserExamAnswerRepository answerRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExamSessionStore examSessionStore;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long testUserId;
    private Long attemptId;
    private List<Long> questionIds;

    @BeforeEach
    void setUp() {
        String unique = String.valueOf(System.nanoTime());
        User user = userRepository.save(User.builder()
                .username("concurrency_test_" + unique)
                .email("concurrency_test_" + unique + "@example.com")
                .passwordHash(passwordEncoder.encode("test-password"))
                .role("ROLE_USER")
                .build());
        testUserId = user.getId();

        questionIds = questionRepository.findRandomByLevel("N5", 5).stream()
                .map(ExamQuestion::getId)
                .toList();
        assertThat(questionIds).isNotEmpty();

        UserExamAttempt attempt = attemptRepository.save(UserExamAttempt.builder()
                .userId(testUserId)
                .jlptLevel("N5")
                .startedAt(LocalDateTime.now())
                .build());
        attemptId = attempt.getId();

        examSessionStore.initSession(attemptId, questionIds, 1800);
    }

    @AfterEach
    void tearDown() {
        answerRepository.deleteAll(answerRepository.findByAttemptId(attemptId));
        attemptRepository.deleteById(attemptId);
        userRepository.deleteById(testUserId);
        examSessionStore.cleanup(attemptId);
    }

    @Test
    void finalize_calledConcurrentlyManyTimes_shouldPersistExactlyOnce() throws InterruptedException {
        int concurrentCalls = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCalls);
        CountDownLatch readyLatch = new CountDownLatch(concurrentCalls);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentCalls);

        for (int i = 0; i < concurrentCalls; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    examFinalizationService.finalize(attemptId, ExamAttemptStatus.COMPLETED);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // thả toàn bộ luồng chạy gần như đồng thời - mô phỏng đúng race thật
        boolean completedInTime = doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();

        UserExamAttempt result = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ExamAttemptStatus.COMPLETED);

        List<UserExamAnswer> answers = answerRepository.findByAttemptId(attemptId);
        // Đúng bằng số câu hỏi - KHÔNG bị nhân đôi/nhân ba dù có 8 luồng cùng gọi finalize().
        assertThat(answers).hasSize(questionIds.size());
    }

    @Test
    void finalize_whenAnswerInsertFails_shouldRollbackStatusBackToInProgress() {
        // Giả lập lỗi ghi answers bằng cách chèn sẵn 1 dòng trùng UNIQUE(attempt_id, question_id).
        Long collidingQuestionId = questionIds.get(0);
        answerRepository.save(UserExamAnswer.builder()
                .attemptId(attemptId)
                .questionId(collidingQuestionId)
                .selectedOption("A")
                .isCorrect(false)
                .build());

        assertThatThrownBy(() -> examFinalizationService.finalize(attemptId, ExamAttemptStatus.COMPLETED))
                .isInstanceOf(DataIntegrityViolationException.class);

        UserExamAttempt result = attemptRepository.findById(attemptId).orElseThrow();
        // Transaction rollback toàn bộ -> status KHÔNG bị kẹt ở trạng thái lỡ dở,
        // quay lại IN_PROGRESS để Reconciliation Job có thể thử lại ở lượt quét sau.
        assertThat(result.getStatus()).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
    }
}
