package com.kanjimastery.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kanjimastery.backend.event.ExamFinalizedEvent;
import com.kanjimastery.backend.listener.ExamFinalizedEventListener;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;

/**
 * Điểm chốt điểm DUY NHẤT, dùng chung cho cả 3 luồng có thể kích hoạt việc
 * chấm điểm một bài thi gần như đồng thời: Submit thủ công, Redis Keyspace
 * Expired Event, và Reconciliation Job. Giải quyết Race Condition bằng UPDATE
 * có điều kiện (compare-and-swap ở tầng SQL) - chỉ luồng khiến số dòng ảnh
 * hưởng > 0 mới được ghi answers/publish event, các luồng thua cuộc no-op.
 *
 * Ranh giới transaction: phần {@code @Transactional} chỉ đụng PostgreSQL.
 * Thao tác Redis (đẩy leaderboard, dọn Hash) được tách sang
 * {@link ExamFinalizedEventListener} chạy SAU khi transaction DB commit,
 * tránh dual-write khiến 2 nguồn dữ liệu lệch pha nếu DB rollback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamFinalizationService {

    private final UserExamAttemptRepository attemptRepository;
    private final ExamQuestionRepository questionRepository;
    private final UserExamAnswerRepository answerRepository;
    private final ExamSessionStore examSessionStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void finalize(Long attemptId, ExamAttemptStatus resultingStatus) {
        UserExamAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || !ExamAttemptStatus.IN_PROGRESS.equals(attempt.getStatus())) {
            // Không tồn tại, hoặc đã có luồng khác chốt điểm trước đó - dừng ngay, không làm gì thêm.
            return;
        }

        List<Long> questionIds = examSessionStore.getQuestionIds(attemptId);
        Map<Long, String> selectedAnswers = examSessionStore.getAnswers(attemptId);

        // Batch-fetch 1 lần duy nhất thay vì findById trong vòng lặp (tránh N+1 Query).
        Map<Long, ExamQuestion> questionsById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now(clock);
        int score = 0;
        List<UserExamAnswer> answerRows = new ArrayList<>();
        for (Long questionId : questionIds) {
            ExamQuestion question = questionsById.get(questionId);
            if (question == null) {
                continue;
            }
            String selected = selectedAnswers.get(questionId);
            boolean correct = selected != null && selected.equalsIgnoreCase(question.getCorrectOption());
            if (correct) {
                score++;
            }
            answerRows.add(UserExamAnswer.builder()
                    .attemptId(attemptId)
                    .questionId(questionId)
                    .selectedOption(selected)
                    .isCorrect(correct)
                    .answeredAt(now)
                    .build());
        }

        int timeSpentSeconds = (int) Duration.between(attempt.getStartedAt(), now).getSeconds();

        int rowsAffected = attemptRepository.finalizeIfInProgress(
                attemptId, resultingStatus, score, timeSpentSeconds, now);

        if (rowsAffected == 0) {
            log.debug("Attempt {} đã được luồng khác chốt điểm trước - bỏ qua.", attemptId);
            return;
        }

        answerRepository.saveAll(answerRows);

        eventPublisher.publishEvent(new ExamFinalizedEvent(
                attemptId, attempt.getUserId(), attempt.getJlptLevel().name(), score, timeSpentSeconds, attempt.getSittingId()));
    }
}
