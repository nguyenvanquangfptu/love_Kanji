package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.service.DraftReviewer.Draft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Nhờ AI (Gemini) viết nháp một đoạn văn 問題3 文章の文法 cho một cấp độ: đoạn văn mới có các chỗ trống 【1】..【n】 (n
 * theo cấu trúc đề), mỗi chỗ trống là một câu hỏi 4 lựa chọn. Đoạn văn được kiểm tra rồi vào hàng chờ duyệt cả đoạn:
 * chỗ trống phải khớp với câu hỏi và câu hỏi đúng cấu trúc (sai thì loại cả đoạn, kèm lý do), từ vượt cấp tính trên
 * cả đoạn, AI giải lại từng chỗ trống khi đọc cả đoạn. Tốn hai request Gemini (viết + giải lại).
 * <p>
 * Không giữ transaction trong lúc chờ Gemini trả lời: chỉ lưu đoạn văn và câu hỏi ở bước cuối, trong một transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassageDraftService {

    /** Chỗ trống AI hay viết lệch: 【１】, [1], ［1］, 【 1 】. */
    private static final Pattern LOOSE_MARKER = Pattern.compile("[【\\[［]\\s*([0-9０-９]+)\\s*[】\\]］]");
    private static final int MAX_TITLE_LENGTH = 200;
    /** Kiểu bài của 文章の文法 theo cấp độ, như đề thật. */
    private static final Map<String, String> STYLE = Map.of(
            "N5", "bài viết ngắn (作文) của một du học sinh về cuộc sống hằng ngày, khoảng 150-250 chữ",
            "N4", "bài viết ngắn (作文) hoặc lá thư, email của một du học sinh, khoảng 250-350 chữ",
            "N3", "bài luận ngắn hoặc bài viết kể một trải nghiệm, khoảng 350-450 chữ");

    private final GeminiClient geminiClient;
    private final DraftReviewer reviewer;
    private final VocabularyLevelChecker levelChecker;
    private final GrammarPointRepository grammarPointRepository;
    private final ExamPassageRepository passageRepository;
    private final ExamQuestionRepository questionRepository;
    private final JlptBlueprintProperties blueprints;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param passageId id đoạn văn đã lưu
     * @param status    DRAFT (chờ duyệt) hoặc REJECTED (sai cấu trúc, lý do ở ghi chú của đoạn)
     * @param flag      cảnh báo nặng nhất của đoạn; null nếu không có
     * @param questions số câu hỏi đọc được
     */
    public record PassageDraftResult(Long passageId, ExamQuestionStatus status, ExamQuestionFlag flag, int questions) {
    }

    public PassageDraftResult draft(String level) {
        if (!geminiClient.isEnabled()) {
            throw new BadRequestException("Chưa cấu hình Gemini (GEMINI_API_KEY) nên chưa sinh nháp được");
        }
        String normalized = level.strip().toUpperCase(Locale.ROOT);
        int blanks = blueprints.section(normalized, ExamSection.GRAMMAR)
                .map(section -> section.getQuestions().getOrDefault(JlptQuestionType.TEXT_GRAMMAR, 0))
                .orElse(0);
        if (blanks == 0) {
            throw new BadRequestException("Đề " + normalized + " không có dạng 文章の文法");
        }
        List<GrammarPoint> points = grammarPointRepository.findByJlptLevelOrderByLessonAscIdAsc(normalized);
        List<String> titles = passageRepository.findByJlptLevelOrderByIdDesc(normalized, PageRequest.of(0, 20))
                .map(ExamPassage::getTitle).filter(StringUtils::hasText).toList();

        String answer = geminiClient.generateJson(prompt(normalized, blanks, points, titles))
                .orElseThrow(() -> new BadRequestException(
                        "Gemini không trả lời (hết hạn mức trong ngày hoặc lỗi mạng) - thử lại sau"));
        JsonNode root = reviewer.json(answer).filter(JsonNode::isObject)
                .orElseThrow(() -> new BadRequestException("Không đọc được đoạn văn Gemini trả về - thử lại"));
        String content = normalizeMarkers(root.path("content").asText("").strip());
        if (content.isEmpty()) {
            throw new BadRequestException("Không đọc được đoạn văn Gemini trả về - thử lại");
        }
        String title = root.path("title").asText("").strip();
        ExamPassage passage = ExamPassage.builder()
                .jlptLevel(normalized)
                .title(title.isEmpty() ? null : title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH)))
                .content(content)
                .source(ExamQuestionSource.AI)
                .build();

        Map<String, Long> pointIds = points.stream().collect(Collectors.toMap(GrammarPoint::getPattern,
                GrammarPoint::getId, (first, second) -> first));
        Set<Integer> seen = new HashSet<>();
        List<Draft> drafts = new ArrayList<>();
        root.path("questions").forEach(item -> question(item, normalized, pointIds)
                .filter(draft -> seen.add(draft.question().getBlankNo()))
                .ifPresent(drafts::add));
        drafts.sort(Comparator.comparing(draft -> draft.question().getBlankNo()));

        List<String> problems = new ArrayList<>(ExamQuestionValidator.passageProblems(content,
                drafts.stream().map(draft -> draft.question().getBlankNo()).toList()));
        if (drafts.size() != blanks) {
            problems.add("đề " + normalized + " cần " + blanks + " chỗ trống, đọc được " + drafts.size());
        }
        List<Draft> wellFormed = reviewer.keepWellFormed(drafts);
        if (wellFormed.size() < drafts.size()) {
            problems.add("có câu hỏi sai cấu trúc (xem từng câu)");
        }
        if (problems.isEmpty()) {
            check(passage, wellFormed);
        } else {
            passage.setStatus(ExamQuestionStatus.REJECTED);
            note(passage, "Loại tự động - " + String.join("; ", problems) + ".");
            drafts.forEach(draft -> draft.question().setStatus(ExamQuestionStatus.REJECTED));
        }

        List<ExamQuestion> questions = drafts.stream().map(Draft::question).toList();
        ExamPassage saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
            ExamPassage stored = passageRepository.save(passage);
            questions.forEach(question -> question.setPassageId(stored.getId()));
            questionRepository.saveAll(questions);
            return stored;
        }));
        log.info("Sinh nháp đoạn văn {} #{}: {} ({} câu hỏi){}", normalized, saved.getId(), saved.getStatus(),
                questions.size(), saved.getFlag() == null ? "" : ", cảnh báo " + saved.getFlag());
        return new PassageDraftResult(saved.getId(), saved.getStatus(), saved.getFlag(), questions.size());
    }

    private static String prompt(String level, int blanks, List<GrammarPoint> points, List<String> titles) {
        String avoid = titles.isEmpty() ? ""
                : "\nĐã có các đoạn văn với tiêu đề sau, hãy viết về chủ đề khác: " + String.join("、", titles) + ".";
        String grammar = points.stream().map(GrammarPoint::getPattern).collect(Collectors.joining("、"));
        return """
                Bạn là giáo viên tiếng Nhật đang soạn đề luyện thi JLPT %1$s, dạng 問題3 文章の文法: một đoạn văn có %2$d \
                chỗ trống, mỗi chỗ trống là một câu hỏi 4 lựa chọn.
                Viết một đoạn văn MỚI do bạn tự nghĩ - không chép đề thi thật, sách hay trang web nào: %3$s. Chỉ dùng \
                từ vựng và chữ Hán tới trình độ %1$s.%4$s
                - Đánh dấu chỗ trống bằng 【1】【2】... theo thứ tự xuất hiện trong đoạn, mỗi số đúng một lần, đủ %2$d \
                chỗ trống.
                - Mỗi chỗ trống kiểm tra ngữ pháp trong mạch văn: liên từ nối câu (それで, でも, それに...), mẫu ngữ \
                pháp, dạng chia của động từ, từ chỉ định (この, その...), cách kết câu. Phải đọc câu trước và sau mới \
                chọn được; chỉ MỘT lựa chọn hợp với mạch văn.
                - "options": đúng 4 lựa chọn ngắn cùng loại; "answer": vị trí (0-3) của lựa chọn đúng.
                - "grammar": mẫu ngữ pháp chỗ trống kiểm tra, chép đúng như trong danh sách dưới đây nếu có, không có \
                thì để "".
                - "explanation": giải thích ngắn bằng tiếng Việt.
                Các mẫu ngữ pháp %1$s: %5$s
                Trả về một object JSON, không thêm gì khác:
                {"title": "...", "content": "...【1】...【2】...", "questions": [{"blank": 1, "options": ["...", "...", \
                "...", "..."], "answer": 0, "grammar": "...", "explanation": "..."}]}
                """.formatted(level, blanks, STYLE.getOrDefault(level, "đoạn văn ngắn"), avoid, grammar);
    }

    /** 【１】, [1], ［1］... thành 【1】. */
    static String normalizeMarkers(String content) {
        Matcher matcher = LOOSE_MARKER.matcher(content);
        StringBuilder normalized = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group(1).chars()
                    .map(digit -> digit >= '０' && digit <= '９' ? digit - '０' + '0' : digit)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
            matcher.appendReplacement(normalized, Matcher.quoteReplacement("【" + Integer.parseInt(digits) + "】"));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private static Optional<Draft> question(JsonNode item, String level, Map<String, Long> pointIds) {
        int blank = item.path("blank").asInt(0);
        List<String> options = DraftReviewer.texts(item.path("options"));
        int answer = item.path("answer").asInt(-1);
        if (blank < 1 || !DraftReviewer.storable(options, answer)) {
            return Optional.empty();
        }
        ExamQuestion question = DraftReviewer.question(level, JlptQuestionType.TEXT_GRAMMAR, "【" + blank + "】", null,
                null, options, options.get(answer), item.path("explanation").asText(""));
        question.setBlankNo(blank);
        Long pointId = pointIds.get(GrammarPointService.normalizePattern(item.path("grammar").asText("")));
        if (pointId != null) {
            question.getGrammarPointIds().add(pointId);
        }
        return Optional.of(new Draft(question, options.get(answer), "Chỗ trống 【" + blank + "】"));
    }

    /** Từ vượt cấp trên cả đoạn (đã điền đáp án đúng), rồi nhờ AI đọc cả đoạn và giải lại từng chỗ trống. */
    private void check(ExamPassage passage, List<Draft> drafts) {
        String filled = passage.getContent();
        for (Draft draft : drafts) {
            filled = filled.replace("【" + draft.question().getBlankNo() + "】", draft.text());
        }
        VocabularyLevelChecker.Result result = levelChecker.open(passage.getJlptLevel()).check(filled);
        if (result.suspicious()) {
            passage.setFlag(ExamQuestionFlag.ABOVE_LEVEL);
        }
        if (!result.describe().isEmpty()) {
            note(passage, result.describe());
        }
        String context = (passage.getTitle() == null ? "" : passage.getTitle() + "\n") + passage.getContent();
        reviewer.checkBySolvingAgain("Đọc đoạn văn rồi chọn lựa chọn điền vào từng chỗ trống cho hợp với mạch văn.",
                context, drafts);
        // Cờ của đoạn là cờ nặng nhất trong đoạn và các câu hỏi.
        for (Draft draft : drafts) {
            ExamQuestionFlag flag = draft.question().getFlag();
            if (flag != null && (passage.getFlag() == null || flag.severity() < passage.getFlag().severity())) {
                passage.setFlag(flag);
            }
        }
    }

    private static void note(ExamPassage passage, String detail) {
        passage.setReviewNote(passage.getReviewNote() == null ? detail : passage.getReviewNote() + " " + detail);
    }
}
