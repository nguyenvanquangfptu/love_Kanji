package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Nộp bài thi thật (PostgreSQL + Redis): listener AFTER_COMMIT đưa từng câu đã trả lời vào ôn tập trong transaction
 * riêng, và gọi lại lần nữa không ghi trùng.
 */
class ExamDiagnosisIT extends AbstractIntegrationTest {

    @Autowired
    private ExamFinalizationService examFinalizationService;
    @Autowired
    private ExamDiagnosisService examDiagnosisService;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private ExamSessionStore examSessionStore;
    @Autowired
    private ReviewLogRepository reviewLogRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long attemptId;
    private ExamQuestion waterQuestion;
    private ExamQuestion goldQuestion;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder()
                .username("exam_diagnosis_" + suffix)
                .email("exam_diagnosis_" + suffix + "@test.local")
                .passwordHash("x")
                .build()).getId();
        // Nạp kèm từ vựng của câu hỏi (quan hệ lazy, test không chạy trong transaction).
        List<ExamQuestion> seed = questionRepository.findAllWithWordsByIdIn(
                questionRepository.findAll().stream().map(ExamQuestion::getId).toList());
        waterQuestion = seed.stream().filter(q -> q.getQuestionText().equals("Kanji 水 có nghĩa là gì?")).findFirst()
                .orElseThrow();
        goldQuestion = seed.stream().filter(q -> q.getQuestionText().equals("Kanji 金 có nghĩa là gì?")).findFirst()
                .orElseThrow();
        attemptId = attemptRepository.save(UserExamAttempt.builder()
                .userId(userId)
                .jlptLevel("N5")
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .build()).getId();
        examSessionStore.initSession(attemptId, List.of(waterQuestion.getId(), goldQuestion.getId()));
    }

    @AfterEach
    void tearDown() {
        // ON DELETE CASCADE dọn luôn lượt thi, câu trả lời, lịch ôn và review_logs của người dùng thử.
        userRepository.deleteById(userId);
        examSessionStore.cleanup(attemptId);
    }

    @Test
    void submittingAnExam_shouldAddTheWordsOfWrongAnswersToReview_onlyOnce() {
        examSessionStore.saveAnswer(attemptId, waterQuestion.getId(), "A");   // sai: 水 là "Nước" (B)
        examSessionStore.saveAnswer(attemptId, goldQuestion.getId(), "B");    // đúng: 金 là "Vàng, tiền"
        Long water = waterQuestion.getKanjiIds().iterator().next();
        Long gold = goldQuestion.getKanjiIds().iterator().next();

        examFinalizationService.finalize(attemptId, ExamAttemptStatus.COMPLETED);

        assertThat(attemptRepository.findById(attemptId).orElseThrow().getDiagnosedAt()).isNotNull();
        assertThat(examLogs())
                .extracting(ReviewLog::getKanjiId, ReviewLog::getDirection, ReviewLog::getCorrect,
                        ReviewLog::getRating, ReviewLog::getScheduled, ReviewLog::getChosenAnswer)
                .containsExactlyInAnyOrder(
                        tuple(water, QuizDirection.MEANING, false, (short) ReviewRating.AGAIN, false, "Lửa"),
                        tuple(gold, QuizDirection.MEANING, true, (short) ReviewRating.GOOD, false, "Vàng, tiền"));
        // Từ làm sai chưa có trong lịch ôn: được thêm vào như từ đã gặp, đến hạn ngay; từ làm đúng thì không.
        assertThat(srsRepository.findByUserIdAndKanjiId(userId, water)).hasValueSatisfying(card ->
                assertThat(card.getNextReviewAt()).isBeforeOrEqualTo(LocalDateTime.now()));
        assertThat(srsRepository.findByUserIdAndKanjiId(userId, gold)).isEmpty();

        assertThat(examDiagnosisService.diagnose(attemptId)).isZero();
        assertThat(examLogs()).hasSize(2);
    }

    private List<ReviewLog> examLogs() {
        return reviewLogRepository.findAll().stream()
                .filter(log -> log.getUserId().equals(userId) && ReviewSource.EXAM.equals(log.getSource()))
                .toList();
    }
}
