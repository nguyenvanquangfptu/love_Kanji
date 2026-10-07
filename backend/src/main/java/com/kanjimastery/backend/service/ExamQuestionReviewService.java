package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.AdminExamQuestionRequest;
import com.kanjimastery.backend.dto.AdminExamQuestionResponse;
import com.kanjimastery.backend.dto.QuestionBankStatsResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionReport;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuestionReportStatus;
import com.kanjimastery.backend.repository.ExamQuestionReportRepository;
import com.kanjimastery.backend.repository.ExamQuestionStatsRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trang duyệt câu thi đề JLPT: lọc câu theo cấp độ / dạng / trạng thái / cảnh báo / điểm ngữ pháp, sửa nội dung,
 * duyệt (vào đề, từng câu hoặc cả trang), loại (kèm lý do), rút khỏi đề; thống kê mỗi dạng đã đủ câu cho bao nhiêu đề.
 */
@Service
@RequiredArgsConstructor
public class ExamQuestionReviewService {

    private final ExamQuestionRepository questionRepository;
    private final KanjiRepository kanjiRepository;
    private final GrammarPointRepository grammarPointRepository;
    private final JlptBlueprintProperties blueprints;
    private final ExamQuestionReportRepository reportRepository;
    private final ExamQuestionStatsRepository statsRepository;

    /**
     * Bộ lọc của trang duyệt; trường null = không lọc. Chỉ có câu thuộc một dạng đề JLPT.
     *
     * @param reportedOnly chỉ câu có báo lỗi của người học đang chờ xem
     */
    public record Filter(String level, JlptQuestionType type, ExamQuestionStatus status, boolean flaggedOnly, Long grammarPointId,
                         boolean reportedOnly) {
    }

    /**
     * @param approved số câu đã duyệt
     * @param skipped  các câu không duyệt, kèm lý do
     */
    public record BulkApproval(int approved, List<Skipped> skipped) {

        public record Skipped(Long id, String reason) {
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminExamQuestionResponse> search(Filter filter, int page, int size) {
        Page<ExamQuestion> found = questionRepository.findAll(specification(filter),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, ExamQuestion> withLinks = found.isEmpty()
                ? Map.of()
                : questionRepository.findAllWithLinksByIdIn(found.map(ExamQuestion::getId).getContent()).stream()
                        .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));
        List<ExamQuestion> questions = found.getContent().stream().map(question -> withLinks.get(question.getId()))
                .toList();
        return new PageImpl<>(toResponses(questions), found.getPageable(), found.getTotalElements());
    }

    private static Specification<ExamQuestion> specification(Filter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNotNull(root.get("questionType")));
            // Câu của đoạn văn 文章の文法 được duyệt theo cả đoạn, ở danh sách đoạn văn.
            predicates.add(builder.isNull(root.get("passageId")));
            if (StringUtils.hasText(filter.level())) {
                predicates.add(builder.equal(root.get("jlptLevel"), Levels.require(filter.level())));
            }
            if (filter.type() != null) {
                predicates.add(builder.equal(root.get("questionType"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("review").get("status"), filter.status()));
            }
            if (filter.flaggedOnly()) {
                predicates.add(builder.isNotNull(root.get("review").get("flag")));
            }
            if (filter.grammarPointId() != null) {
                predicates.add(builder.isMember(filter.grammarPointId(), root.<Set<Long>>get("grammarPointIds")));
            }
            if (filter.reportedOnly()) {
                Subquery<Long> openReports = query.subquery(Long.class);
                Root<ExamQuestionReport> report = openReports.from(ExamQuestionReport.class);
                openReports.select(report.get("id")).where(builder.equal(report.get("questionId"), root.get("id")),
                        builder.equal(report.get("status"), QuestionReportStatus.OPEN));
                predicates.add(builder.exists(openReports));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Sửa nội dung; câu phải đúng cấu trúc của dạng câu. Không đổi trạng thái duyệt. */
    @Transactional
    public AdminExamQuestionResponse update(Long id, AdminExamQuestionRequest request) {
        ExamQuestion question = questionWithLinks(id);
        List<String> problems = ExamQuestionValidator.problems(question.getQuestionType(), request.getQuestionText(),
                request.getSentence(), request.getHighlight(),
                List.of(request.getOptionA(), request.getOptionB(), request.getOptionC(), request.getOptionD()),
                request.getCorrectOption());
        if (!problems.isEmpty()) {
            throw new BadRequestException("Câu hỏi chưa hợp lệ: " + String.join("; ", problems));
        }
        question.setQuestionText(request.getQuestionText().strip());
        question.setSentence(StringUtils.hasText(request.getSentence()) ? request.getSentence().strip() : null);
        question.setHighlight(StringUtils.hasText(request.getHighlight()) ? request.getHighlight().strip() : null);
        question.setOptionA(request.getOptionA().strip());
        question.setOptionB(request.getOptionB().strip());
        question.setOptionC(request.getOptionC().strip());
        question.setOptionD(request.getOptionD().strip());
        question.setCorrectOption(request.getCorrectOption());
        question.setExplanation(StringUtils.hasText(request.getExplanation()) ? request.getExplanation().strip() : null);
        if (request.getGrammarPointIds() != null) {
            Set<Long> ids = new HashSet<>(request.getGrammarPointIds());
            if (grammarPointRepository.findAllById(ids).size() != ids.size()) {
                throw new BadRequestException("Có điểm ngữ pháp không tồn tại");
            }
            question.getGrammarPointIds().clear();
            question.getGrammarPointIds().addAll(ids);
        }
        return toResponse(question);
    }

    /**
     * Duyệt (câu vào đề được), loại (bắt buộc ghi lý do), rút khỏi đề, hoặc đưa về chờ duyệt. Câu sai cấu trúc không
     * duyệt được; duyệt rồi thì cảnh báo của bước kiểm tra tự động coi như đã được người duyệt xem.
     */
    @Transactional
    public AdminExamQuestionResponse changeStatus(Long id, ExamQuestionStatus status, String note) {
        ExamQuestion question = questionWithLinks(id);
        if (question.getPassageId() != null) {
            throw new BadRequestException("Câu này thuộc một đoạn văn - duyệt hoặc loại cả đoạn văn");
        }
        if (ExamQuestionStatus.REJECTED.equals(status) && !StringUtils.hasText(note)) {
            throw new BadRequestException("Loại câu hỏi thì cần ghi lý do");
        }
        if (ExamQuestionStatus.APPROVED.equals(status)) {
            List<String> problems = problems(question);
            if (!problems.isEmpty()) {
                throw new BadRequestException("Chưa duyệt được, câu hỏi cần sửa: " + String.join("; ", problems));
            }
        }
        LocalDateTime now = LocalDateTime.now();
        question.getReview().decide(status, now);
        if (StringUtils.hasText(note)) {
            question.getReview().replaceNote(note.strip());
        }
        // Người duyệt đã quyết định về câu: các báo lỗi đang mở coi như đã xử lý.
        reportRepository.closeOpen(List.of(id), QuestionReportStatus.RESOLVED, now);
        return toResponse(question);
    }

    /**
     * Bỏ qua các báo lỗi đang mở của một câu (người duyệt xem và thấy câu không sai); bỏ cờ báo lỗi nếu có. Không đổi
     * trạng thái: câu đã bị rút tự động thì người duyệt duyệt lại để đưa vào đề.
     */
    @Transactional
    public AdminExamQuestionResponse dismissReports(Long id) {
        ExamQuestion question = questionWithLinks(id);
        reportRepository.closeOpen(List.of(id), QuestionReportStatus.DISMISSED, LocalDateTime.now());
        if (question.getFlag() == ExamQuestionFlag.REPORTED) {
            question.getReview().clearFlag();
        }
        return toResponse(question);
    }

    /**
     * Duyệt một lượt các câu người duyệt đã đọc trên trang. Chỉ duyệt câu đang chờ duyệt, không có cảnh báo (câu có
     * cảnh báo cần xem và duyệt riêng), không thuộc đoạn văn và đúng cấu trúc; câu khác giữ nguyên, kèm lý do.
     */
    @Transactional
    public BulkApproval approveAll(Collection<Long> ids) {
        Map<Long, ExamQuestion> found = questionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));
        List<BulkApproval.Skipped> skipped = new ArrayList<>();
        int approved = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : new LinkedHashSet<>(ids)) {
            ExamQuestion question = found.get(id);
            String reason = question == null ? "không tìm thấy" : whyNotApprovable(question);
            if (reason != null) {
                skipped.add(new BulkApproval.Skipped(id, reason));
                continue;
            }
            question.getReview().decide(ExamQuestionStatus.APPROVED, now);
            approved++;
        }
        return new BulkApproval(approved, skipped);
    }

    /** Lý do không duyệt cùng lượt được; null nếu duyệt được. */
    private static String whyNotApprovable(ExamQuestion question) {
        if (question.getPassageId() != null) {
            return "thuộc một đoạn văn - duyệt cả đoạn văn";
        }
        if (!ExamQuestionStatus.DRAFT.equals(question.getStatus())) {
            return "không ở trạng thái chờ duyệt";
        }
        if (question.getFlag() != null) {
            return "có cảnh báo - cần xem và duyệt riêng";
        }
        List<String> problems = problems(question);
        return problems.isEmpty() ? null : "câu hỏi cần sửa: " + String.join("; ", problems);
    }

    private static List<String> problems(ExamQuestion question) {
        return ExamQuestionValidator.problems(question.getQuestionType(), question.getQuestionText(),
                question.getSentence(), question.getHighlight(), List.of(question.getOptionA(), question.getOptionB(),
                        question.getOptionC(), question.getOptionD()), question.getCorrectOption());
    }

    /** Theo các cấp độ có cấu trúc đề: mỗi dạng câu có bao nhiêu câu ở mỗi trạng thái, đủ cho bao nhiêu đề. */
    @Transactional(readOnly = true)
    public List<QuestionBankStatsResponse> stats() {
        Map<JlptLevel, Map<JlptQuestionType, long[]>> counts = new HashMap<>();
        for (ExamQuestionRepository.BankCount count : questionRepository.countByLevelTypeAndStatus()) {
            long[] byStatus = counts.computeIfAbsent(count.getLevel(), level -> new HashMap<>())
                    .computeIfAbsent(count.getType(), type -> new long[4]);
            int slot = switch (count.getStatus()) {
                case ExamQuestionStatus.APPROVED -> 0;
                case ExamQuestionStatus.DRAFT -> 1;
                case ExamQuestionStatus.REJECTED -> 2;
                default -> 3;
            };
            byStatus[slot] += count.getCount();
        }
        List<QuestionBankStatsResponse> result = new ArrayList<>();
        blueprints.getLevels().forEach((level, blueprint) -> {
            List<QuestionBankStatsResponse.TypeStats> types = new ArrayList<>();
            for (JlptBlueprintProperties.Section section : blueprint.getSections()) {
                section.getQuestions().forEach((type, perExam) -> {
                    long[] byStatus = counts.getOrDefault(level, Map.of()).getOrDefault(type, new long[4]);
                    types.add(new QuestionBankStatsResponse.TypeStats(section.getName(), type, perExam, byStatus[0],
                            byStatus[1], byStatus[2], byStatus[3], byStatus[0] / perExam));
                });
            }
            result.add(new QuestionBankStatsResponse(level, types));
        });
        return result;
    }

    private ExamQuestion questionWithLinks(Long id) {
        return questionRepository.findAllWithLinksByIdIn(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi: " + id));
    }

    private AdminExamQuestionResponse toResponse(ExamQuestion question) {
        return toResponses(List.of(question)).get(0);
    }

    /** Câu hỏi (đã nạp từ vựng, điểm ngữ pháp) dạng hiện trên trang duyệt, giữ thứ tự. */
    List<AdminExamQuestionResponse> toResponses(List<ExamQuestion> questions) {
        Map<Long, Kanji> words = kanjiRepository.findAllById(questions.stream()
                        .flatMap(question -> question.getKanjiIds().stream()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        Map<Long, GrammarPoint> points = grammarPointRepository.findAllById(questions.stream()
                        .flatMap(question -> question.getGrammarPointIds().stream()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(GrammarPoint::getId, Function.identity()));
        Map<Long, List<AdminExamQuestionResponse.Report>> reports = questions.isEmpty()
                ? Map.of()
                : reportRepository.findByQuestionIdInAndStatusOrderByCreatedAtAsc(
                                questions.stream().map(ExamQuestion::getId).toList(), QuestionReportStatus.OPEN)
                        .stream()
                        .collect(Collectors.groupingBy(ExamQuestionReport::getQuestionId,
                                Collectors.mapping(report -> new AdminExamQuestionResponse.Report(report.getReason(),
                                        report.getNote(), report.getCreatedAt()), Collectors.toList())));
        Map<Long, AdminExamQuestionResponse.Stats> stats = questions.isEmpty()
                ? Map.of()
                : statsRepository.findAllById(questions.stream().map(ExamQuestion::getId).toList()).stream()
                        .collect(Collectors.toMap(row -> row.getQuestionId(),
                                row -> new AdminExamQuestionResponse.Stats(row.getResponses(), row.getCorrectRate(),
                                        row.getDiscrimination(), row.getComputedAt())));
        return questions.stream()
                .map(question -> toResponse(question, words, points,
                        reports.getOrDefault(question.getId(), List.of()), stats.get(question.getId())))
                .toList();
    }

    private static AdminExamQuestionResponse toResponse(ExamQuestion question, Map<Long, Kanji> words,
                                                        Map<Long, GrammarPoint> points,
                                                        List<AdminExamQuestionResponse.Report> reports,
                                                        AdminExamQuestionResponse.Stats stats) {
        return AdminExamQuestionResponse.builder()
                .id(question.getId())
                .jlptLevel(question.getJlptLevel())
                .questionType(question.getQuestionType())
                .skill(question.getSkill())
                .status(question.getStatus())
                .flag(question.getFlag())
                .reviewNote(question.getReviewNote())
                .reviewedAt(question.getReviewedAt())
                .source(question.getSource())
                .questionText(question.getQuestionText())
                .sentence(question.getSentence())
                .highlight(question.getHighlight())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .correctOption(question.getCorrectOption())
                .explanation(question.getExplanation())
                .passageId(question.getPassageId())
                .blankNo(question.getBlankNo())
                .words(question.getKanjiIds().stream().map(words::get).filter(word -> word != null)
                        .map(word -> new AdminExamQuestionResponse.Word(word.getId(), word.getCharacter(),
                                word.getReading()))
                        .toList())
                .grammarPoints(question.getGrammarPointIds().stream().map(points::get).filter(point -> point != null)
                        .map(point -> new AdminExamQuestionResponse.Grammar(point.getId(), point.getPattern()))
                        .toList())
                .reports(reports)
                .stats(stats)
                .build();
    }
}
