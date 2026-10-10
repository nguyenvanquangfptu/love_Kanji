package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.AdminExamPassageResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.QuestionReportStatus;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionReportRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Duyệt đoạn văn 文章の文法: cả đoạn cùng các câu hỏi của nó được duyệt, loại hay rút một lần - câu hỏi của đoạn luôn
 * cùng trạng thái với đoạn. Sửa nội dung đoạn văn thì các chỗ trống 【n】 phải vẫn khớp với câu hỏi.
 */
@Service
@RequiredArgsConstructor
public class ExamPassageReviewService {

    private final ExamPassageRepository passageRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamQuestionReviewService questionReviewService;
    private final ExamQuestionReportRepository reportRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminExamPassageResponse> search(String level, ExamQuestionStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ExamPassage> found = status != null
                ? passageRepository.findByJlptLevelAndReviewStatusOrderByIdDesc(Levels.require(level), status, pageRequest)
                : passageRepository.findByJlptLevelOrderByIdDesc(Levels.require(level), pageRequest);
        Map<Long, List<ExamQuestion>> questions = questionsOf(found.map(ExamPassage::getId).getContent());
        return new PageImpl<>(found.getContent().stream()
                .map(passage -> toResponse(passage, questions.getOrDefault(passage.getId(), List.of())))
                .toList(), found.getPageable(), found.getTotalElements());
    }

    @Transactional
    public AdminExamPassageResponse update(Long id, String title, String content) {
        ExamPassage passage = passage(id);
        List<ExamQuestion> questions = questionsOf(List.of(id)).getOrDefault(id, List.of());
        List<String> problems = ExamQuestionValidator.passageProblems(content, blankNos(questions));
        if (!problems.isEmpty()) {
            throw new BadRequestException("Đoạn văn chưa hợp lệ: " + String.join("; ", problems));
        }
        passage.setTitle(StringUtils.hasText(title) ? title.strip() : null);
        passage.setContent(content.strip());
        return toResponse(passage, questions);
    }

    /** Đổi trạng thái cả đoạn và mọi câu hỏi của nó; loại thì cần lý do, duyệt thì đoạn và mọi câu phải hợp lệ. */
    @Transactional
    public AdminExamPassageResponse changeStatus(Long id, ExamQuestionStatus status, String note) {
        if (ExamQuestionStatus.REJECTED.equals(status) && !StringUtils.hasText(note)) {
            throw new BadRequestException("Loại đoạn văn thì cần ghi lý do");
        }
        ExamPassage passage = passage(id);
        List<ExamQuestion> questions = questionsOf(List.of(id)).getOrDefault(id, List.of());
        if (ExamQuestionStatus.APPROVED.equals(status)) {
            List<String> problems = new ArrayList<>(
                    ExamQuestionValidator.passageProblems(passage.getContent(), blankNos(questions)));
            for (ExamQuestion question : questions) {
                ExamQuestionValidator.problems(question.getQuestionType(), question.getQuestionText(),
                                question.getSentence(), question.getHighlight(), List.of(question.getOptionA(),
                                        question.getOptionB(), question.getOptionC(), question.getOptionD()),
                                question.getCorrectOption())
                        .forEach(problem -> problems.add("【" + question.getBlankNo() + "】 " + problem));
            }
            if (!problems.isEmpty()) {
                throw new BadRequestException("Chưa duyệt được, đoạn văn cần sửa: " + String.join("; ", problems));
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        passage.getReview().decide(status, now);
        if (StringUtils.hasText(note)) {
            passage.getReview().replaceNote(note.strip());
        }
        for (ExamQuestion question : questions) {
            question.getReview().decide(status, now);
        }
        if (!questions.isEmpty()) {
            // Người duyệt đã quyết định về cả đoạn: các báo lỗi đang mở của các câu coi như đã xử lý.
            reportRepository.closeOpen(questions.stream().map(ExamQuestion::getId).toList(),
                    QuestionReportStatus.RESOLVED, now);
        }
        return toResponse(passage, questions);
    }

    private ExamPassage passage(Long id) {
        return passageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đoạn văn: " + id));
    }

    /** Câu hỏi của các đoạn văn, theo thứ tự chỗ trống. */
    private Map<Long, List<ExamQuestion>> questionsOf(List<Long> passageIds) {
        if (passageIds.isEmpty()) {
            return Map.of();
        }
        return questionRepository.findAllWithLinksByPassageIdIn(passageIds).stream()
                .sorted(Comparator.comparing(ExamQuestion::getBlankNo))
                .collect(Collectors.groupingBy(ExamQuestion::getPassageId));
    }

    private static List<Integer> blankNos(List<ExamQuestion> questions) {
        return questions.stream().map(ExamQuestion::getBlankNo).toList();
    }

    private AdminExamPassageResponse toResponse(ExamPassage passage, List<ExamQuestion> questions) {
        return AdminExamPassageResponse.builder()
                .id(passage.getId())
                .jlptLevel(passage.getJlptLevel())
                .title(passage.getTitle())
                .content(passage.getContent())
                .status(passage.getStatus())
                .source(passage.getSource())
                .sourceRef(passage.getSourceRef())
                .flag(passage.getFlag())
                .reviewNote(passage.getReviewNote())
                .reviewedAt(passage.getReviewedAt())
                .questions(questionReviewService.toResponses(questions))
                .build();
    }
}
