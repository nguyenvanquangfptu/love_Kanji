package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kanjimastery.backend.config.ExamProperties;
import com.kanjimastery.backend.dto.ExamQuestionPublicResponse;
import com.kanjimastery.backend.dto.ExamResultResponse;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.dto.ExamSessionResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.dto.SaveAnswerRequest;
import com.kanjimastery.backend.dto.StartExamRequest;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamQuestionRepository questionRepository;
    private final UserExamAttemptRepository attemptRepository;
    private final UserExamAnswerRepository answerRepository;
    private final ExamSessionStore examSessionStore;
    private final ExamFinalizationService examFinalizationService;
    private final ExamProperties examProperties;

    @Transactional
    public StartExamResponse start(Long userId, StartExamRequest request) {
        String level = request.getJlptLevel().toUpperCase();
        int count = request.getQuestionCount() != null ? request.getQuestionCount() : examProperties.getDefaultQuestionCount();

        List<ExamQuestion> questions = questionRepository.findRandomByLevel(level, count);
        if (questions.isEmpty()) {
            throw new BadRequestException("Không có câu hỏi nào cho cấp độ: " + level);
        }

        UserExamAttempt attempt = UserExamAttempt.builder()
                .userId(userId)
                .jlptLevel(level)
                .startedAt(LocalDateTime.now())
                .build();
        attempt = attemptRepository.save(attempt);

        List<Long> questionIds = questions.stream().map(ExamQuestion::getId).toList();
        examSessionStore.initSession(attempt.getId(), questionIds);

        List<ExamQuestionPublicResponse> publicQuestions = questions.stream()
                .map(ExamQuestionPublicResponse::from)
                .toList();

        return StartExamResponse.builder()
                .attemptId(attempt.getId())
                .jlptLevel(level)
                .questions(publicQuestions)
                .remainingSeconds(examProperties.getDurationSeconds())
                .startedAt(attempt.getStartedAt())
                .build();
    }

    public void saveAnswer(Long userId, Long attemptId, SaveAnswerRequest request) {
        getOwnedInProgressAttempt(attemptId, userId);
        String option = request.getSelectedOption() != null ? request.getSelectedOption().toUpperCase() : null;
        examSessionStore.saveAnswer(attemptId, request.getQuestionId(), option);
    }

    public ExamSessionResponse getSession(Long userId, Long attemptId) {
        UserExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        long elapsed = Duration.between(attempt.getStartedAt(), LocalDateTime.now()).getSeconds();
        long remaining = Math.max(0, examProperties.getDurationSeconds() - elapsed);

        return ExamSessionResponse.builder()
                .attemptId(attemptId)
                .remainingSeconds(remaining)
                .answers(examSessionStore.getAnswers(attemptId))
                .build();
    }

    public ExamResultResponse submit(Long userId, Long attemptId) {
        getOwnedInProgressAttempt(attemptId, userId);
        examFinalizationService.finalize(attemptId, ExamAttemptStatus.COMPLETED);

        UserExamAttempt result = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt thi: " + attemptId));
        return toResultResponse(result);
    }

    public ExamReviewResponse getReview(Long userId, Long attemptId) {
        UserExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        if (ExamAttemptStatus.IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BadRequestException("Bài thi chưa được nộp, không thể xem lại");
        }

        List<UserExamAnswer> answers = answerRepository.findByAttemptId(attemptId);
        List<Long> questionIds = answers.stream().map(UserExamAnswer::getQuestionId).toList();
        Map<Long, ExamQuestion> questionsById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        List<QuestionReviewItem> items = answers.stream()
                .map(answer -> {
                    ExamQuestion question = questionsById.get(answer.getQuestionId());
                    return QuestionReviewItem.builder()
                            .questionId(answer.getQuestionId())
                            .questionText(question.getQuestionText())
                            .optionA(question.getOptionA())
                            .optionB(question.getOptionB())
                            .optionC(question.getOptionC())
                            .optionD(question.getOptionD())
                            .correctOption(question.getCorrectOption())
                            .selectedOption(answer.getSelectedOption())
                            .correct(Boolean.TRUE.equals(answer.getIsCorrect()))
                            .explanation(question.getExplanation())
                            .build();
                })
                .toList();

        return ExamReviewResponse.builder()
                .attemptId(attemptId)
                .jlptLevel(attempt.getJlptLevel())
                .status(attempt.getStatus())
                .totalScore(attempt.getTotalScore())
                .totalQuestions(items.size())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .questions(items)
                .build();
    }

    private UserExamAttempt getOwnedAttempt(Long attemptId, Long userId) {
        UserExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt thi: " + attemptId));
        if (!attempt.getUserId().equals(userId)) {
            // Coi như không tồn tại để tránh lộ thông tin lượt thi của người khác.
            throw new ResourceNotFoundException("Không tìm thấy lượt thi: " + attemptId);
        }
        return attempt;
    }

    private UserExamAttempt getOwnedInProgressAttempt(Long attemptId, Long userId) {
        UserExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        if (!ExamAttemptStatus.IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BadRequestException("Bài thi đã kết thúc, không thể thao tác tiếp");
        }
        return attempt;
    }

    private ExamResultResponse toResultResponse(UserExamAttempt attempt) {
        return ExamResultResponse.builder()
                .attemptId(attempt.getId())
                .status(attempt.getStatus())
                .totalScore(attempt.getTotalScore())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .submittedAt(attempt.getSubmittedAt())
                .build();
    }
}
