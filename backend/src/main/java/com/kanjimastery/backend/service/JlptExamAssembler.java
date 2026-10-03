package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ghép câu hỏi cho một phần của đề JLPT theo cấu trúc đề ({@link JlptBlueprintProperties}). */
@Component
@RequiredArgsConstructor
public class JlptExamAssembler {

    /** Lấy dư câu ứng viên để vẫn đủ câu khi phải bỏ các câu hỏi lại từ đã hỏi. */
    private static final int SPARE_CANDIDATES = 20;
    /** Số đoạn văn ứng viên cho 文章の文法. */
    private static final int CANDIDATE_PASSAGES = 10;

    private final ExamQuestionRepository questionRepository;
    private final ExamPassageRepository passageRepository;

    /** Một 問題 của đề: số thứ tự trong đề thật, dạng câu, số câu theo đề thật và các câu đã chọn. */
    public record Mondai(int number, String type, int plannedCount, List<ExamQuestion> questions) {
    }

    /**
     * Mỗi 問題 lấy ngẫu nhiên tối đa đủ số câu đã duyệt đúng dạng, đúng cấp độ - dạng chưa đủ câu thì có bao nhiêu
     * lấy bấy nhiêu. Mỗi từ chỉ được hỏi một câu trong cả buổi thi: {@code askedWords} là các từ đã hỏi ở phần trước,
     * được thêm dần các từ của phần này. 文章の文法 lấy trọn đoạn văn (mọi câu hỏi của đoạn, theo thứ tự chỗ trống).
     * Kết quả theo thứ tự 問題1, 問題2..., kể cả 問題 không có câu nào.
     */
    public List<Mondai> assemble(String level, JlptBlueprintProperties.Section section, Set<Long> askedWords) {
        List<Mondai> mondai = new ArrayList<>();
        int number = 0;
        for (Map.Entry<String, Integer> entry : section.getQuestions().entrySet()) {
            number++;
            int planned = entry.getValue();
            if (JlptQuestionType.TEXT_GRAMMAR.equals(entry.getKey())) {
                mondai.add(new Mondai(number, entry.getKey(), planned, passageQuestions(level, planned, askedWords)));
                continue;
            }
            List<Long> candidateIds = questionRepository
                    .findRandomByLevelAndType(level, entry.getKey(), planned * 2 + SPARE_CANDIDATES).stream()
                    .map(ExamQuestion::getId)
                    .toList();
            Map<Long, ExamQuestion> withWords = candidateIds.isEmpty()
                    ? Map.of()
                    : questionRepository.findAllWithWordsByIdIn(candidateIds).stream()
                            .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

            List<ExamQuestion> picked = new ArrayList<>();
            for (Long id : candidateIds) {
                ExamQuestion question = withWords.get(id);
                if (picked.size() >= planned) {
                    break;
                }
                if (question != null && Collections.disjoint(question.getKanjiIds(), askedWords)) {
                    picked.add(question);
                    askedWords.addAll(question.getKanjiIds());
                }
            }
            mondai.add(new Mondai(number, entry.getKey(), planned, picked));
        }
        return mondai;
    }

    /** Các đoạn văn đã duyệt, lấy trọn từng đoạn sao cho tổng số câu không quá {@code planned}. */
    private List<ExamQuestion> passageQuestions(String level, int planned, Set<Long> askedWords) {
        List<ExamPassage> passages = passageRepository.findRandomApproved(level, CANDIDATE_PASSAGES);
        if (passages.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ExamQuestion>> byPassage = questionRepository
                .findAllWithLinksByPassageIdIn(passages.stream().map(ExamPassage::getId).toList()).stream()
                .filter(question -> ExamQuestionStatus.APPROVED.equals(question.getStatus()))
                .sorted(Comparator.comparing(ExamQuestion::getBlankNo))
                .collect(Collectors.groupingBy(ExamQuestion::getPassageId));
        List<ExamQuestion> picked = new ArrayList<>();
        for (ExamPassage passage : passages) {
            List<ExamQuestion> questions = byPassage.getOrDefault(passage.getId(), List.of());
            boolean freshWords = questions.stream().allMatch(question -> Collections.disjoint(question.getKanjiIds(),
                    askedWords));
            if (!questions.isEmpty() && picked.size() + questions.size() <= planned && freshWords) {
                picked.addAll(questions);
                questions.forEach(question -> askedWords.addAll(question.getKanjiIds()));
            }
        }
        return picked;
    }
}
