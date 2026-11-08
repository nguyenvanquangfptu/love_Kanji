package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.AdminExamPassageResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.QuestionReportStatus;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionReportRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Duyệt đoạn văn (文章の文法 của phần Ngữ pháp, bài đọc của phần Đọc hiểu): cả đoạn cùng các câu hỏi của nó được duyệt,
 * loại hay rút một lần - câu hỏi của đoạn luôn cùng trạng thái với đoạn. Sửa nội dung đoạn văn thì đoạn vẫn phải hợp
 * lệ với câu hỏi của nó (chỗ trống 【n】, phần gạch chân...).
 */
@Service
@RequiredArgsConstructor
public class ExamPassageReviewService {

    private final ExamPassageRepository passageRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamQuestionReviewService questionReviewService;
    private final ExamQuestionReportRepository reportRepository;
    private final JlptBlueprintProperties blueprints;
    private final Clock clock;

    /**
     * Đoạn văn của một cấp độ, mới nhất trước; {@code section}, {@code type} (dạng câu của các câu hỏi trong đoạn),
     * {@code status} null = không lọc theo tiêu chí đó.
     */
    @Transactional(readOnly = true)
    public Page<AdminExamPassageResponse> search(String level, ExamSection section, JlptQuestionType type,
                                                 ExamQuestionStatus status, int page, int size) {
        return search(level, section, type, status, null, page, size);
    }

    /**
     * Như {@link #search(String, ExamSection, JlptQuestionType, ExamQuestionStatus, int, int)}, thêm {@code testCode}:
     * chỉ đoạn nhập từ một đề tự soạn (mã đề, vd. N3-DH01), xếp theo thứ tự trong đề.
     */
    @Transactional(readOnly = true)
    public Page<AdminExamPassageResponse> search(String level, ExamSection section, JlptQuestionType type,
                                                 ExamQuestionStatus status, String testCode, int page, int size) {
        Sort.Direction direction = StringUtils.hasText(testCode) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<ExamPassage> found = passageRepository.findAll(specification(level, section, type, status, testCode),
                PageRequest.of(page, size, Sort.by(direction, "id")));
        Map<Long, List<ExamQuestion>> questions = questionsOf(found.map(ExamPassage::getId).getContent());
        return new PageImpl<>(found.getContent().stream()
                .map(passage -> toResponse(passage, questions.getOrDefault(passage.getId(), List.of())))
                .toList(), found.getPageable(), found.getTotalElements());
    }

    private static Specification<ExamPassage> specification(String level, ExamSection section, JlptQuestionType type,
                                                            ExamQuestionStatus status, String testCode) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("jlptLevel"), Levels.require(level)));
            if (StringUtils.hasText(testCode)) {
                // Mã nguồn của đoạn nhập từ đề tự soạn: "<mã đề>/NP/19-23", "<mã đề>/DH/28-30".
                predicates.add(builder.like(root.get("sourceRef"), testCode.strip() + "/%"));
            }
            if (section != null) {
                predicates.add(builder.equal(root.get("section"), section));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("review").get("status"), status));
            }
            if (type != null) {
                // Một đoạn chỉ có câu hỏi một dạng: lọc theo dạng là có câu hỏi dạng đó.
                Subquery<Long> ofType = query.subquery(Long.class);
                Root<ExamQuestion> question = ofType.from(ExamQuestion.class);
                ofType.select(question.get("id")).where(builder.equal(question.get("passageId"), root.get("id")),
                        builder.equal(question.get("questionType"), type));
                predicates.add(builder.exists(ofType));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public AdminExamPassageResponse update(Long id, String title, String content) {
        ExamPassage passage = passage(id);
        List<ExamQuestion> questions = questionsOf(List.of(id)).getOrDefault(id, List.of());
        List<String> problems = passageProblems(passage, content, questions);
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
            List<String> problems = approvalProblems(passage, questions);
            if (!problems.isEmpty()) {
                throw new BadRequestException("Chưa duyệt được, đoạn văn cần sửa: " + String.join("; ", problems));
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        decide(passage, questions, status, now);
        if (StringUtils.hasText(note)) {
            passage.getReview().replaceNote(note.strip());
        }
        return toResponse(passage, questions);
    }

    /**
     * Duyệt một lượt các đoạn người duyệt đã đọc trên trang. Chỉ duyệt đoạn đang chờ duyệt, không có cảnh báo (ở đoạn
     * hay ở câu hỏi của đoạn - cần xem và duyệt riêng) và hợp lệ như khi duyệt từng đoạn; đoạn khác giữ nguyên, kèm lý do.
     */
    @Transactional
    public ExamQuestionReviewService.BulkApproval approveAll(Collection<Long> ids) {
        Map<Long, ExamPassage> found = passageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ExamPassage::getId, Function.identity()));
        Map<Long, List<ExamQuestion>> questions = questionsOf(List.copyOf(found.keySet()));
        List<ExamQuestionReviewService.BulkApproval.Skipped> skipped = new ArrayList<>();
        int approved = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long id : new LinkedHashSet<>(ids)) {
            ExamPassage passage = found.get(id);
            List<ExamQuestion> ofPassage = questions.getOrDefault(id, List.of());
            String reason = passage == null ? "không tìm thấy" : whyNotApprovable(passage, ofPassage);
            if (reason != null) {
                skipped.add(new ExamQuestionReviewService.BulkApproval.Skipped(id, reason));
                continue;
            }
            decide(passage, ofPassage, ExamQuestionStatus.APPROVED, now);
            approved++;
        }
        return new ExamQuestionReviewService.BulkApproval(approved, skipped);
    }

    /**
     * Duyệt mọi đoạn chờ duyệt khớp bộ lọc (không chỉ một trang): cùng điều kiện như {@link #approveAll}, đoạn không đạt
     * giữ nguyên. Luôn là đoạn chờ duyệt, bất kể trạng thái trên giao diện.
     */
    @Transactional
    public ExamQuestionReviewService.BulkApproval approveAllMatching(String level, ExamSection section,
                                                                    JlptQuestionType type, String testCode) {
        List<ExamPassage> found = passageRepository.findAll(
                specification(level, section, type, ExamQuestionStatus.DRAFT, testCode), Sort.by("id"));
        Map<Long, List<ExamQuestion>> questions = questionsOf(found.stream().map(ExamPassage::getId).toList());
        List<ExamQuestionReviewService.BulkApproval.Skipped> skipped = new ArrayList<>();
        int skippedTotal = 0;
        int approved = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (ExamPassage passage : found) {
            List<ExamQuestion> ofPassage = questions.getOrDefault(passage.getId(), List.of());
            String reason = whyNotApprovable(passage, ofPassage);
            if (reason != null) {
                skippedTotal++;
                if (skipped.size() < ExamQuestionReviewService.BulkApproval.MAX_SKIPPED_SHOWN) {
                    skipped.add(new ExamQuestionReviewService.BulkApproval.Skipped(passage.getId(), reason));
                }
                continue;
            }
            decide(passage, ofPassage, ExamQuestionStatus.APPROVED, now);
            approved++;
        }
        return new ExamQuestionReviewService.BulkApproval(approved, skipped, skippedTotal);
    }

    /** Lý do không duyệt cùng lượt được; null nếu duyệt được. */
    private String whyNotApprovable(ExamPassage passage, List<ExamQuestion> questions) {
        if (!ExamQuestionStatus.DRAFT.equals(passage.getStatus())) {
            return "không ở trạng thái chờ duyệt";
        }
        if (passage.getFlag() != null || questions.stream().anyMatch(question -> question.getFlag() != null)) {
            return "có cảnh báo - cần xem và duyệt riêng";
        }
        List<String> problems = approvalProblems(passage, questions);
        return problems.isEmpty() ? null : "đoạn văn cần sửa: " + String.join("; ", problems);
    }

    /** Lỗi khiến chưa duyệt được: đoạn văn và mọi câu hỏi của nó phải hợp lệ. */
    private List<String> approvalProblems(ExamPassage passage, List<ExamQuestion> questions) {
        List<String> problems = new ArrayList<>(passageProblems(passage, passage.getContent(), questions));
        String label = ExamSection.READING.equals(passage.getSection()) ? "Câu " : "【";
        String end = ExamSection.READING.equals(passage.getSection()) ? ":" : "】";
        for (ExamQuestion question : questions) {
            ExamQuestionValidator.problems(question.getQuestionType(), question.getQuestionText(),
                            question.getSentence(), question.getHighlight(), List.of(question.getOptionA(),
                                    question.getOptionB(), question.getOptionC(), question.getOptionD()),
                            question.getCorrectOption())
                    .forEach(problem -> problems.add(label + question.getBlankNo() + end + " " + problem));
        }
        return problems;
    }

    /** Đổi trạng thái cả đoạn cùng mọi câu hỏi của nó. */
    private void decide(ExamPassage passage, List<ExamQuestion> questions, ExamQuestionStatus status,
                        LocalDateTime now) {
        passage.getReview().decide(status, now);
        for (ExamQuestion question : questions) {
            question.getReview().decide(status, now);
        }
        if (!questions.isEmpty()) {
            // Người duyệt đã quyết định về cả đoạn: các báo lỗi đang mở của các câu coi như đã xử lý.
            reportRepository.closeOpen(questions.stream().map(ExamQuestion::getId).toList(),
                    QuestionReportStatus.RESOLVED, now);
        }
    }

    private ExamPassage passage(Long id) {
        return passageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đoạn văn: " + id));
    }

    /** Lỗi của đoạn văn với nội dung {@code content}, theo phần thi và số câu mỗi bài trong cấu trúc đề. */
    private List<String> passageProblems(ExamPassage passage, String content, List<ExamQuestion> questions) {
        Integer perPassage = questions.isEmpty() || questions.get(0).getQuestionType() == null ? null
                : blueprints.section(passage.getJlptLevel(), passage.getSection())
                        .flatMap(section -> section.questionsPerPassage(questions.get(0).getQuestionType()))
                        .orElse(null);
        return ExamQuestionValidator.passageProblems(passage.getSection(), content, questions.stream()
                .map(question -> new ExamQuestionValidator.PassageQuestion(question.getQuestionType(),
                        question.getBlankNo()))
                .toList(), perPassage);
    }

    /** Câu hỏi của các đoạn văn, theo thứ tự chỗ trống (thứ tự câu với bài đọc). */
    private Map<Long, List<ExamQuestion>> questionsOf(List<Long> passageIds) {
        if (passageIds.isEmpty()) {
            return Map.of();
        }
        return questionRepository.findAllWithLinksByPassageIdIn(passageIds).stream()
                .sorted(Comparator.comparing(ExamQuestion::getBlankNo))
                .collect(Collectors.groupingBy(ExamQuestion::getPassageId));
    }

    private AdminExamPassageResponse toResponse(ExamPassage passage, List<ExamQuestion> questions) {
        return AdminExamPassageResponse.builder()
                .id(passage.getId())
                .jlptLevel(passage.getJlptLevel())
                .section(passage.getSection())
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
