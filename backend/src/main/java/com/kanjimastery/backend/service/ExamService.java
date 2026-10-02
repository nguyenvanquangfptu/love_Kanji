package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.KanjiRepository;
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
    private final KanjiRepository kanjiRepository;

    @Transactional
    public StartExamResponse start(Long userId, StartExamRequest request) {
        String level = request.getJlptLevel().toUpperCase();
        int count = request.getQuestionCount() != null ? request.getQuestionCount() : examProperties.getDefaultQuestionCount();

        List<ExamQuestion> questions = oneQuestionPerWord(questionRepository.findRandomByLevel(level, count * 3), count);
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

    /**
     * Lấy dư câu ngẫu nhiên rồi giữ tối đa {@code count} câu không hỏi lại một từ đã có câu: câu hỏi nghĩa ghi kèm
     * cách đọc sẽ lộ đáp án câu hỏi đọc của cùng từ đó.
     */
    private List<ExamQuestion> oneQuestionPerWord(List<ExamQuestion> candidates, int count) {
        Map<Long, ExamQuestion> withWords = questionRepository
                .findAllWithWordsByIdIn(candidates.stream().map(ExamQuestion::getId).toList()).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));
        Set<Long> askedWords = new HashSet<>();
        List<ExamQuestion> picked = new ArrayList<>();
        for (ExamQuestion candidate : candidates) {
            if (picked.size() >= count) {
                break;
            }
            ExamQuestion question = withWords.get(candidate.getId());
            if (question != null && Collections.disjoint(question.getKanjiIds(), askedWords)) {
                picked.add(question);
                askedWords.addAll(question.getKanjiIds());
            }
        }
        return picked;
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
        Map<Long, ExamQuestion> questionsById = questionRepository.findAllWithWordsByIdIn(questionIds).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        List<QuestionReviewItem> items = answers.stream()
                .map(answer -> {
                    ExamQuestion question = questionsById.get(answer.getQuestionId());
                    return QuestionReviewItem.builder()
                            .questionId(answer.getQuestionId())
                            .questionText(question.getQuestionText())
                            .sentence(question.getSentence())
                            .highlight(question.getHighlight())
                            .optionA(question.getOptionA())
                            .optionB(question.getOptionB())
                            .optionC(question.getOptionC())
                            .optionD(question.getOptionD())
                            .correctOption(question.getCorrectOption())
                            .selectedOption(answer.getSelectedOption())
                            .correct(Boolean.TRUE.equals(answer.getIsCorrect()))
                            .explanation(question.getExplanation())
                            .skill(question.getSkill())
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
                .skills(skillScores(items))
                .wrongWords(wrongWords(answers, questionsById))
                .addedToReview(attempt.getDiagnosedAt() != null)
                .build();
    }

    /** Từ của các câu đã trả lời sai, theo thứ tự câu hỏi, không lặp. */
    private List<ExamReviewResponse.Word> wrongWords(List<UserExamAnswer> answers, Map<Long, ExamQuestion> questionsById) {
        Set<Long> kanjiIds = new LinkedHashSet<>();
        for (UserExamAnswer answer : answers) {
            ExamQuestion question = questionsById.get(answer.getQuestionId());
            if (question != null && answer.getSelectedOption() != null && !Boolean.TRUE.equals(answer.getIsCorrect())) {
                kanjiIds.addAll(question.getKanjiIds());
            }
        }
        if (kanjiIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Kanji> words = kanjiRepository.findAllById(kanjiIds).stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        return kanjiIds.stream()
                .map(words::get)
                .filter(word -> word != null)
                .map(word -> new ExamReviewResponse.Word(word.getId(), word.getCharacter(), word.getReading(),
                        word.getMeaning()))
                .toList();
    }

    /** Số câu đúng trên số câu theo từng kỹ năng: đọc, viết, rồi nghĩa. */
    static List<ExamReviewResponse.SkillScore> skillScores(List<QuestionReviewItem> items) {
        Map<String, int[]> bySkill = new LinkedHashMap<>();
        for (String skill : List.of(QuizDirection.KANJI_TO_READING, QuizDirection.READING_TO_KANJI, QuizDirection.MEANING)) {
            bySkill.put(skill, new int[2]);
        }
        for (QuestionReviewItem item : items) {
            int[] tally = item.getSkill() == null ? null : bySkill.get(item.getSkill());
            if (tally != null) {
                tally[1]++;
                if (item.isCorrect()) {
                    tally[0]++;
                }
            }
        }
        return bySkill.entrySet().stream()
                .filter(entry -> entry.getValue()[1] > 0)
                .map(entry -> new ExamReviewResponse.SkillScore(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
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
