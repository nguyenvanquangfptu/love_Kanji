package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Nhờ AI (Gemini) viết nháp câu thi ngữ pháp cho một điểm ngữ pháp, rồi kiểm tra tự động trước khi tới tay người
 * duyệt: đúng cấu trúc của dạng câu (sai thì loại luôn, kèm lý do), từ vựng không vượt cấp độ, và nhờ AI giải lại câu
 * hỏi (không cho xem đáp án) để bắt câu sai đáp án hoặc có hơn một đáp án đúng. Câu qua cấu trúc thì vào hàng chờ duyệt,
 * có cảnh báo thì gắn cờ. Mỗi lần sinh tốn hai request Gemini (viết + giải lại).
 * <p>
 * Không giữ transaction trong lúc chờ Gemini trả lời: chỉ lưu câu ở bước cuối.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionDraftService {

    /** Số câu tối đa mỗi lần sinh (một request). */
    static final int MAX_DRAFTS = 8;
    static final String GRAMMAR_FORM_TEXT = "Chọn từ hoặc mẫu ngữ pháp thích hợp điền vào chỗ trống.";
    static final String SENTENCE_ORDER_TEXT = "Sắp xếp các vế thành câu đúng rồi chọn vế ở vị trí ★.";
    private static final Set<String> DRAFTABLE = Set.of(JlptQuestionType.GRAMMAR_FORM, JlptQuestionType.SENTENCE_ORDER);
    /** Ô trống AI hay viết lệch: （ ）, (　　), （　）... */
    private static final Pattern LOOSE_BLANK = Pattern.compile("[（(][\\s　]*[）)]");
    private static final String LETTERS = "ABCD";

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final GrammarPointRepository grammarPointRepository;
    private final ExamQuestionRepository questionRepository;
    private final VocabularyLevelChecker levelChecker;

    /**
     * @param drafted    số câu vào hàng chờ duyệt (kể cả câu có cảnh báo)
     * @param flagged    trong đó, số câu có cảnh báo
     * @param rejected   số câu sai cấu trúc, bị loại luôn
     * @param unreadable số mục AI trả về không đọc được, bỏ qua
     */
    public record DraftResult(int drafted, int flagged, int rejected, int unreadable) {
    }

    /** Một câu nháp đọc được từ AI, đã dựng thành câu thi; {@code fullSentence} là câu hoàn chỉnh để kiểm tra từ. */
    record Draft(ExamQuestion question, String fullSentence) {
    }

    public DraftResult draft(Long grammarPointId, String type, int count) {
        if (!geminiClient.isEnabled()) {
            throw new BadRequestException("Chưa cấu hình Gemini (GEMINI_API_KEY) nên chưa sinh nháp được");
        }
        if (!DRAFTABLE.contains(type)) {
            throw new BadRequestException("Chỉ sinh nháp được dạng 文法形式の判断 và 文の組み立て");
        }
        GrammarPoint point = grammarPointRepository.findById(grammarPointId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm ngữ pháp: " + grammarPointId));
        int wanted = Math.max(1, Math.min(count, MAX_DRAFTS));
        List<String> existing = questionRepository.findSentencesByGrammarPoint(grammarPointId);

        String answer = geminiClient.generateJson(draftPrompt(point, type, wanted, existing))
                .orElseThrow(() -> new BadRequestException(
                        "Gemini không trả lời (hết hạn mức trong ngày hoặc lỗi mạng) - thử lại sau"));
        List<JsonNode> items = items(answer);
        List<Draft> drafts = new ArrayList<>();
        int unreadable = 0;
        for (JsonNode item : items) {
            Optional<Draft> draft = JlptQuestionType.GRAMMAR_FORM.equals(type) ? grammarForm(item, point)
                    : sentenceOrder(item, point);
            if (draft.isPresent()) {
                drafts.add(draft.get());
            } else {
                unreadable++;
            }
        }

        List<Draft> wellFormed = new ArrayList<>();
        int rejected = 0;
        for (Draft draft : drafts) {
            ExamQuestion question = draft.question();
            List<String> problems = ExamQuestionValidator.problems(type, question.getQuestionText(),
                    question.getSentence(), question.getHighlight(), options(question), question.getCorrectOption());
            if (problems.isEmpty()) {
                wellFormed.add(draft);
            } else {
                question.setStatus(ExamQuestionStatus.REJECTED);
                question.setReviewNote("Loại tự động - sai cấu trúc: " + String.join("; ", problems));
                rejected++;
            }
        }
        checkLevel(point.getJlptLevel(), wellFormed);
        checkBySolvingAgain(type, wellFormed);

        questionRepository.saveAll(drafts.stream().map(Draft::question).toList());
        int flagged = (int) wellFormed.stream().filter(draft -> draft.question().getFlag() != null).count();
        log.info("Sinh nháp {} cho 「{}」 ({}): {} chờ duyệt ({} có cảnh báo), {} bị loại, {} không đọc được",
                type, point.getPattern(), point.getJlptLevel(), wellFormed.size(), flagged, rejected, unreadable);
        return new DraftResult(wellFormed.size(), flagged, rejected, unreadable);
    }

    private static String draftPrompt(GrammarPoint point, String type, int count, List<String> existing) {
        String grammar = "「%s」 - nghĩa: %s%s".formatted(point.getPattern(), point.getMeaningVi(),
                StringUtils.hasText(point.getConnection()) ? " - cách nối: " + point.getConnection() : "");
        String avoid = existing.isEmpty() ? "" : "\nĐã có các câu sau, hãy viết câu khác hẳn về ngữ cảnh:\n"
                + existing.stream().limit(10).map(sentence -> "- " + sentence).collect(Collectors.joining("\n"));
        String common = """
                Bạn là giáo viên tiếng Nhật đang soạn câu hỏi luyện thi JLPT %s.
                Điểm ngữ pháp cần kiểm tra: %s.
                Viết %d câu hỏi MỚI do bạn tự nghĩ - không chép đề thi thật, sách hay trang web nào.
                Chỉ dùng từ vựng và chữ Hán tới trình độ %s. Mỗi câu chỉ có MỘT đáp án đúng; không lựa chọn nào khác \
                cũng chấp nhận được. Các câu khác nhau về ngữ cảnh.%s
                """.formatted(point.getJlptLevel(), grammar, count, point.getJlptLevel(), avoid);
        if (JlptQuestionType.GRAMMAR_FORM.equals(type)) {
            return common + """
                    Dạng 問題1 文法形式の判断: một câu (hoặc hội thoại ngắn A「…」B「…」) có đúng MỘT chỗ trống viết là \
                    （　　）; chọn cái điền vào chỗ trống.
                    - "options": đúng 4 lựa chọn ngắn: 1 lựa chọn dùng đúng điểm ngữ pháp trên, 3 lựa chọn nhiễu là mẫu \
                    ngữ pháp, trợ từ hoặc dạng chia dễ nhầm cùng trình độ, điền vào thì sai rõ ràng.
                    - "answer": vị trí (0-3) của lựa chọn đúng trong "options".
                    - "explanation": giải thích ngắn bằng tiếng Việt vì sao đúng và các lựa chọn kia sai ở đâu.
                    Trả về một mảng JSON, không thêm gì khác:
                    [{"sentence": "...（　　）...", "options": ["...", "...", "...", "..."], "answer": 0, "explanation": "..."}]
                    """;
        }
        return common + """
                Dạng 問題2 文の組み立て: một câu hoàn chỉnh có dùng điểm ngữ pháp trên, trong đó có 4 vế liền nhau bị \
                bỏ trống; người thi sắp xếp 4 vế rồi chọn vế nằm ở ô ★.
                - "before": phần câu đứng trước 4 vế; "after": phần câu đứng sau 4 vế (có thể rỗng).
                - "parts": đúng 4 vế ngắn, THEO ĐÚNG THỨ TỰ trong câu: before + parts[0] + parts[1] + parts[2] + \
                parts[3] + after là câu hoàn chỉnh. 4 vế chỉ có MỘT cách sắp xếp đúng.
                - "star": vị trí ô ★ (1-4), thường là 3.
                - "explanation": giải thích ngắn bằng tiếng Việt.
                Trả về một mảng JSON, không thêm gì khác:
                [{"before": "...", "parts": ["...", "...", "...", "..."], "after": "...", "star": 3, "explanation": "..."}]
                """;
    }

    /** Mảng câu hỏi trong câu trả lời của AI (mảng, hoặc object có mảng "questions"); JSON hỏng thì rỗng. */
    private List<JsonNode> items(String answer) {
        try {
            JsonNode root = objectMapper.readTree(GeminiClient.stripCodeFence(answer.strip()));
            JsonNode array = root.isArray() ? root : root.path("questions");
            List<JsonNode> items = new ArrayList<>();
            array.forEach(items::add);
            return items;
        } catch (Exception ex) {
            log.warn("Không đọc được JSON câu nháp từ Gemini: {}", ex.getMessage());
            return List.of();
        }
    }

    private Optional<Draft> grammarForm(JsonNode item, GrammarPoint point) {
        String sentence = LOOSE_BLANK.matcher(item.path("sentence").asText("").strip())
                .replaceAll(ExamQuestionGenerator.BLANK);
        List<String> options = texts(item.path("options"));
        int answer = item.path("answer").asInt(-1);
        if (sentence.isEmpty() || options.size() != 4 || answer < 0 || answer > 3) {
            return Optional.empty();
        }
        String correct = options.get(answer);
        List<String> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled);
        ExamQuestion question = question(point, JlptQuestionType.GRAMMAR_FORM, GRAMMAR_FORM_TEXT, sentence, shuffled,
                shuffled.indexOf(correct), item.path("explanation").asText("").strip());
        return Optional.of(new Draft(question, sentence.replace(ExamQuestionGenerator.BLANK, correct)));
    }

    private Optional<Draft> sentenceOrder(JsonNode item, GrammarPoint point) {
        String before = item.path("before").asText("").strip();
        String after = item.path("after").asText("").strip();
        List<String> parts = texts(item.path("parts"));
        int star = item.path("star").asInt(3);
        if (parts.size() != 4 || star < 1 || star > 4) {
            return Optional.empty();
        }
        String slots = IntStream.rangeClosed(1, 4)
                .mapToObj(slot -> slot == star ? ExamQuestionValidator.ORDER_STAR : ExamQuestionValidator.ORDER_SLOT)
                .collect(Collectors.joining(" "));
        String sentence = (before + " " + slots + " " + after).strip();
        String full = before + String.join("", parts) + after;
        String correct = parts.get(star - 1);
        List<String> shuffled = new ArrayList<>(parts);
        Collections.shuffle(shuffled);
        String explanation = (item.path("explanation").asText("").strip() + " Câu đúng: " + full + " ("
                + String.join(" → ", parts) + "). Vế ở vị trí ★ là 「" + correct + "」.").strip();
        ExamQuestion question = question(point, JlptQuestionType.SENTENCE_ORDER, SENTENCE_ORDER_TEXT, sentence,
                shuffled, shuffled.indexOf(correct), explanation);
        return Optional.of(new Draft(question, full));
    }

    private static ExamQuestion question(GrammarPoint point, String type, String text, String sentence,
                                         List<String> options, int correctIndex, String explanation) {
        return ExamQuestion.builder()
                .jlptLevel(point.getJlptLevel())
                .questionText(text)
                .sentence(sentence)
                .optionA(options.get(0))
                .optionB(options.get(1))
                .optionC(options.get(2))
                .optionD(options.get(3))
                .correctOption(String.valueOf(LETTERS.charAt(correctIndex)))
                .explanation(explanation.isEmpty() ? null : explanation)
                .questionType(type)
                .status(ExamQuestionStatus.DRAFT)
                .source(ExamQuestionSource.AI)
                .grammarPointIds(new HashSet<>(Set.of(point.getId())))
                .build();
    }

    private void checkLevel(String level, List<Draft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        VocabularyLevelChecker.Session session = levelChecker.open(level);
        for (Draft draft : drafts) {
            VocabularyLevelChecker.Result result = session.check(draft.fullSentence());
            if (result.suspicious()) {
                flag(draft.question(), ExamQuestionFlag.ABOVE_LEVEL, result.describe());
            } else if (!result.describe().isEmpty()) {
                note(draft.question(), result.describe());
            }
        }
    }

    /**
     * Nhờ AI giải lại các câu (không kèm đáp án): chọn khác đáp án đã cho thì gắn cờ sai đáp án, thấy lựa chọn khác
     * cũng đúng thì gắn cờ nghi có hai đáp án. Gemini không trả lời thì ghi chú để người duyệt tự kiểm tra kỹ.
     */
    private void checkBySolvingAgain(String type, List<Draft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        Map<Integer, JsonNode> verdicts = new HashMap<>();
        geminiClient.generateJson(solvePrompt(type, drafts)).ifPresent(answer ->
                items(answer).forEach(item -> verdicts.put(item.path("index").asInt(-1), item)));
        for (int index = 0; index < drafts.size(); index++) {
            ExamQuestion question = drafts.get(index).question();
            JsonNode verdict = verdicts.get(index + 1);
            if (verdict == null) {
                note(question, "Chưa nhờ AI giải lại được - người duyệt tự kiểm tra kỹ.");
                continue;
            }
            String reason = verdict.path("note").asText("").strip();
            int chosen = verdict.path("answer").asInt(0);
            String correct = question.getCorrectOption();
            if (chosen < 1 || chosen > 4 || LETTERS.charAt(chosen - 1) != correct.charAt(0)) {
                flag(question, ExamQuestionFlag.WRONG_ANSWER, "AI giải lại chọn "
                        + (chosen >= 1 && chosen <= 4 ? LETTERS.charAt(chosen - 1) : "?") + " thay vì " + correct
                        + (reason.isEmpty() ? "." : ": " + reason));
                continue;
            }
            List<String> others = texts(verdict.path("alsoCorrect")).stream()
                    .filter(value -> value.matches("[1-4]"))
                    .map(value -> String.valueOf(LETTERS.charAt(Integer.parseInt(value) - 1)))
                    .filter(letter -> !letter.equals(correct))
                    .toList();
            if (!others.isEmpty()) {
                flag(question, ExamQuestionFlag.AMBIGUOUS, "AI giải lại thấy " + String.join(", ", others)
                        + " cũng có thể đúng" + (reason.isEmpty() ? "." : ": " + reason));
            }
        }
    }

    private static String solvePrompt(String type, List<Draft> drafts) {
        StringBuilder questions = new StringBuilder();
        for (int index = 0; index < drafts.size(); index++) {
            ExamQuestion question = drafts.get(index).question();
            List<String> options = options(question);
            questions.append("Câu ").append(index + 1).append(": ").append(question.getSentence()).append('\n');
            for (int option = 0; option < 4; option++) {
                questions.append("  ").append(option + 1).append(") ").append(options.get(option)).append('\n');
            }
        }
        String task = JlptQuestionType.GRAMMAR_FORM.equals(type)
                ? "Mỗi câu chọn lựa chọn điền vào chỗ trống （　　）."
                : "Mỗi câu sắp xếp 4 vế vào 4 ô ＿＿＿ cho thành câu đúng, rồi cho biết vế nào nằm ở ô ★.";
        return """
                Bạn là thí sinh rất giỏi tiếng Nhật đang làm đề JLPT. %s Xét kỹ từng lựa chọn: có lựa chọn nào khác \
                cũng chấp nhận được không (về ngữ pháp và nghĩa)?
                %s
                Trả về một mảng JSON, không thêm gì khác: [{"index": số thứ tự câu, "answer": số thứ tự lựa chọn đúng \
                nhất (1-4), "alsoCorrect": [số thứ tự các lựa chọn khác cũng chấp nhận được, có thể rỗng], "note": \
                "lý do ngắn bằng tiếng Việt"}]
                """.formatted(task, questions);
    }

    private static void flag(ExamQuestion question, String flag, String detail) {
        // Một câu chỉ giữ một cờ: sai đáp án nặng nhất, rồi tới hai đáp án, rồi tới từ vượt cấp.
        List<String> severity = List.of(ExamQuestionFlag.WRONG_ANSWER, ExamQuestionFlag.AMBIGUOUS,
                ExamQuestionFlag.ABOVE_LEVEL);
        if (question.getFlag() == null || severity.indexOf(flag) < severity.indexOf(question.getFlag())) {
            question.setFlag(flag);
        }
        note(question, detail);
    }

    private static void note(ExamQuestion question, String detail) {
        question.setReviewNote(question.getReviewNote() == null ? detail : question.getReviewNote() + " " + detail);
    }

    private static List<String> options(ExamQuestion question) {
        return List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD());
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asText("").strip()));
        }
        return values;
    }
}
