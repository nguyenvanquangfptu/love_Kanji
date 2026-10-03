package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.PracticeQuestionResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.dto.WeakGrammarResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chẩn đoán ngữ pháp sau các đề JLPT: các điểm ngữ pháp người học hay làm sai gần đây, và bài luyện lại bằng các câu đã
 * duyệt của những điểm đó (không tính giờ, chấm ngay, không lưu kết quả).
 */
@Service
@RequiredArgsConstructor
public class GrammarPracticeService {

    /** Chỉ xét các đề trong chừng ấy ngày gần nhất. */
    public static final int MISTAKE_DAYS = 60;
    public static final int MAX_WEAK_POINTS = 8;
    public static final int MAX_PRACTICE_QUESTIONS = 15;
    /** Số điểm ngữ pháp tối đa trong một bài luyện. */
    static final int MAX_PRACTICE_POINTS = 20;

    private final GrammarPointRepository grammarPointRepository;
    private final ExamQuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<WeakGrammarResponse> weakPoints(Long userId, String level) {
        return grammarPointRepository.findMistakesOfLearner(userId, normalize(level),
                        LocalDateTime.now().minusDays(MISTAKE_DAYS), MAX_WEAK_POINTS).stream()
                .map(row -> new WeakGrammarResponse(row.getId(), row.getPattern(), row.getMeaningVi(),
                        row.getWrong().intValue(), row.getAnswered().intValue()))
                .toList();
    }

    /** Các câu luyện theo thứ tự ngẫu nhiên; rỗng nếu chưa có câu nào đã duyệt cho các điểm này. */
    @Transactional(readOnly = true)
    public List<PracticeQuestionResponse> practice(String level, List<Long> grammarPointIds) {
        Set<Long> ids = grammarPointIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new BadRequestException("Chưa chọn điểm ngữ pháp nào để luyện");
        }
        if (ids.size() > MAX_PRACTICE_POINTS) {
            throw new BadRequestException("Mỗi lần luyện tối đa " + MAX_PRACTICE_POINTS + " điểm ngữ pháp");
        }
        List<Long> questionIds = questionRepository.findRandomForGrammarPoints(normalize(level), ids,
                MAX_PRACTICE_QUESTIONS).stream().map(ExamQuestion::getId).toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ExamQuestion> questions = questionRepository.findAllWithLinksByIdIn(questionIds).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));
        Map<Long, GrammarPoint> points = grammarPointRepository.findAllById(questions.values().stream()
                        .flatMap(question -> question.getGrammarPointIds().stream()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(GrammarPoint::getId, Function.identity()));
        return questionIds.stream().map(questions::get).filter(Objects::nonNull)
                .map(question -> toResponse(question, points))
                .toList();
    }

    private static PracticeQuestionResponse toResponse(ExamQuestion question, Map<Long, GrammarPoint> points) {
        List<QuestionReviewItem.Grammar> grammar = question.getGrammarPointIds().stream().sorted().map(points::get)
                .filter(Objects::nonNull)
                .map(point -> new QuestionReviewItem.Grammar(point.getId(), point.getPattern(), point.getMeaningVi()))
                .toList();
        return new PracticeQuestionResponse(question.getId(), question.getQuestionType(), question.getQuestionText(),
                question.getSentence(), question.getHighlight(), question.getOptionA(), question.getOptionB(),
                question.getOptionC(), question.getOptionD(), question.getCorrectOption(), question.getExplanation(),
                grammar);
    }

    private static String normalize(String level) {
        return level.strip().toUpperCase(Locale.ROOT);
    }
}
