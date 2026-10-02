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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kanjimastery.backend.config.ExamProperties;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamMondaiResponse;
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

    /** Thứ tự kỹ năng khi chia câu thi và khi báo điểm: đọc, viết, nghĩa. */
    private static final List<String> SKILLS =
            List.of(QuizDirection.KANJI_TO_READING, QuizDirection.READING_TO_KANJI, QuizDirection.MEANING);

    private final ExamQuestionRepository questionRepository;
    private final UserExamAttemptRepository attemptRepository;
    private final UserExamAnswerRepository answerRepository;
    private final ExamSessionStore examSessionStore;
    private final ExamFinalizationService examFinalizationService;
    private final ExamProperties examProperties;
    private final KanjiRepository kanjiRepository;
    private final JlptBlueprintProperties blueprints;

    @Transactional
    public StartExamResponse start(Long userId, StartExamRequest request) {
        String level = request.getJlptLevel().toUpperCase();
        int count = request.getQuestionCount() != null ? request.getQuestionCount() : examProperties.getDefaultQuestionCount();

        List<ExamQuestion> questions = pickQuestions(level, count);
        if (questions.isEmpty()) {
            throw new BadRequestException("Không có câu hỏi nào cho cấp độ: " + level);
        }

        return begin(UserExamAttempt.builder().userId(userId).jlptLevel(level).build(), questions, null);
    }

    /**
     * Lưu lượt thi với các câu đã chọn (theo thứ tự hiển thị) và bắt đầu tính giờ theo thời gian làm bài của lượt -
     * dùng chung cho thi nhanh và từng phần của đề JLPT ({@code mondai}: các 問題 của phần thi, null với thi nhanh).
     */
    @Transactional
    public StartExamResponse begin(UserExamAttempt attempt, List<ExamQuestion> questions,
                                   List<ExamMondaiResponse> mondai) {
        attempt.setStartedAt(LocalDateTime.now());
        UserExamAttempt saved = attemptRepository.save(attempt);
        int durationSeconds = examProperties.durationOf(saved);
        examSessionStore.initSession(saved.getId(), questions.stream().map(ExamQuestion::getId).toList(),
                durationSeconds);

        return StartExamResponse.builder()
                .attemptId(saved.getId())
                .jlptLevel(saved.getJlptLevel())
                .questions(questions.stream().map(ExamQuestionPublicResponse::from).toList())
                .remainingSeconds(durationSeconds)
                .startedAt(saved.getStartedAt())
                .sittingId(saved.getSittingId())
                .section(saved.getSection())
                .mondai(mondai)
                .build();
    }

    /**
     * Câu thi ngẫu nhiên, lần lượt lấy từng kỹ năng (đọc, viết, nghĩa, rồi câu chưa phân loại) để bài thi chia đều các
     * kỹ năng - ngân hàng câu sinh từ kho từ vựng có nhiều câu hỏi nghĩa hơn hẳn. Mỗi từ tối đa một câu: câu hỏi nghĩa
     * ghi kèm cách đọc sẽ lộ đáp án câu hỏi đọc của cùng từ đó. Xáo thứ tự ở cuối.
     */
    private List<ExamQuestion> pickQuestions(String level, int count) {
        List<List<ExamQuestion>> candidates = new ArrayList<>();
        for (String skill : SKILLS) {
            candidates.add(questionRepository.findRandomByLevelAndSkill(level, skill, count * 2));
        }
        candidates.add(questionRepository.findRandomUnclassifiedByLevel(level, count * 2));
        Map<Long, ExamQuestion> withWords = questionRepository
                .findAllWithWordsByIdIn(candidates.stream().flatMap(List::stream).map(ExamQuestion::getId).toList())
                .stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        List<Iterator<ExamQuestion>> queues = candidates.stream().map(List::iterator).toList();
        Set<Long> askedWords = new HashSet<>();
        List<ExamQuestion> picked = new ArrayList<>();
        boolean tookAny = true;
        while (picked.size() < count && tookAny) {
            tookAny = false;
            for (Iterator<ExamQuestion> queue : queues) {
                if (picked.size() >= count) {
                    break;
                }
                while (queue.hasNext()) {
                    ExamQuestion question = withWords.get(queue.next().getId());
                    if (question != null && Collections.disjoint(question.getKanjiIds(), askedWords)) {
                        picked.add(question);
                        askedWords.addAll(question.getKanjiIds());
                        tookAny = true;
                        break;
                    }
                }
            }
        }
        Collections.shuffle(picked);
        return picked;
    }

    public void saveAnswer(Long userId, Long attemptId, SaveAnswerRequest request) {
        UserExamAttempt attempt = getOwnedInProgressAttempt(attemptId, userId);
        String option = request.getSelectedOption() != null ? request.getSelectedOption().toUpperCase() : null;
        examSessionStore.saveAnswer(attemptId, request.getQuestionId(), option, examProperties.durationOf(attempt));
    }

    public ExamSessionResponse getSession(Long userId, Long attemptId) {
        UserExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        long elapsed = Duration.between(attempt.getStartedAt(), LocalDateTime.now()).getSeconds();
        long remaining = Math.max(0, examProperties.durationOf(attempt) - elapsed);

        return ExamSessionResponse.builder()
                .attemptId(attemptId)
                .remainingSeconds(remaining)
                .answers(examSessionStore.getAnswers(attemptId))
                .sittingId(attempt.getSittingId())
                .section(attempt.getSection())
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

        List<UserExamAnswer> answers = answerRepository.findByAttemptIdOrderByIdAsc(attemptId);
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
                            .questionType(question.getQuestionType())
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
                .sittingId(attempt.getSittingId())
                .section(attempt.getSection())
                .mondai(mondaiScores(attempt, items))
                .build();
    }

    /** Điểm theo từng 問題 của phần đề JLPT, theo thứ tự trong đề; thi nhanh thì rỗng. */
    private List<ExamReviewResponse.MondaiScore> mondaiScores(UserExamAttempt attempt, List<QuestionReviewItem> items) {
        if (attempt.getSection() == null) {
            return List.of();
        }
        List<String> types = blueprints.section(attempt.getJlptLevel(), attempt.getSection())
                .map(JlptBlueprintProperties.Section::types)
                .orElse(List.of());
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (String type : types) {
            byType.put(type, new int[2]);
        }
        for (QuestionReviewItem item : items) {
            if (item.getQuestionType() != null) {
                int[] tally = byType.computeIfAbsent(item.getQuestionType(), type -> new int[2]);
                tally[1]++;
                if (item.isCorrect()) {
                    tally[0]++;
                }
            }
        }
        return byType.entrySet().stream()
                .filter(entry -> entry.getValue()[1] > 0)
                .map(entry -> new ExamReviewResponse.MondaiScore(types.indexOf(entry.getKey()) + 1, entry.getKey(),
                        entry.getValue()[0], entry.getValue()[1]))
                .toList();
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
        for (String skill : SKILLS) {
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
                .sittingId(attempt.getSittingId())
                .build();
    }
}
