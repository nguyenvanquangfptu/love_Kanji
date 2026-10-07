package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Các bước kiểm tra chung cho câu thi do AI (Gemini) viết nháp, trước khi tới tay người duyệt: đúng cấu trúc của dạng
 * câu (sai thì loại luôn), từ vượt cấp độ, và nhờ AI giải lại câu hỏi (không cho xem đáp án) để bắt câu sai đáp án
 * hoặc có hơn một đáp án đúng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DraftReviewer {

    static final String LETTERS = "ABCD";

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final VocabularyLevelChecker levelChecker;

    /**
     * Một câu nháp đã dựng thành câu thi.
     *
     * @param text   phần người thi đọc (câu hoàn chỉnh đã điền đáp án đúng, các lựa chọn là câu...) - để kiểm tra từ
     *               vượt cấp
     * @param prompt nội dung câu hỏi đưa cho AI giải lại (không có đáp án)
     */
    record Draft(ExamQuestion question, String text, String prompt) {
    }

    /** Phần JSON trong câu trả lời của AI; hỏng thì rỗng. */
    Optional<JsonNode> json(String answer) {
        try {
            return Optional.of(objectMapper.readTree(GeminiClient.stripCodeFence(answer.strip())));
        } catch (Exception ex) {
            log.warn("Không đọc được JSON từ Gemini: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Mảng các mục trong câu trả lời của AI (mảng, hoặc object có mảng "questions"); JSON hỏng thì rỗng. */
    List<JsonNode> items(String answer) {
        List<JsonNode> items = new ArrayList<>();
        json(answer).ifPresent(root -> (root.isArray() ? root : root.path("questions")).forEach(items::add));
        return items;
    }

    /** Loại các câu sai cấu trúc (kèm lý do); trả về các câu đúng cấu trúc. */
    List<Draft> keepWellFormed(List<Draft> drafts) {
        List<Draft> wellFormed = new ArrayList<>();
        for (Draft draft : drafts) {
            ExamQuestion question = draft.question();
            List<String> problems = ExamQuestionValidator.problems(question.getQuestionType(),
                    question.getQuestionText(), question.getSentence(), question.getHighlight(), options(question),
                    question.getCorrectOption());
            if (problems.isEmpty()) {
                wellFormed.add(draft);
            } else {
                question.setStatus(ExamQuestionStatus.REJECTED);
                note(question, "Loại tự động - sai cấu trúc: " + String.join("; ", problems));
            }
        }
        return wellFormed;
    }

    /** Ghi chú từ vượt cấp / ngoài kho; nhiều từ vượt cấp thì gắn cờ. */
    void checkLevel(JlptLevel level, List<Draft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        VocabularyLevelChecker.Session session = levelChecker.open(level);
        for (Draft draft : drafts) {
            VocabularyLevelChecker.Result result = session.check(draft.text());
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
     *
     * @param task    việc người giải phải làm với mỗi câu
     * @param context phần chung cho mọi câu (vd. đoạn văn); null nếu không có
     */
    void checkBySolvingAgain(String task, String context, List<Draft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        Map<Integer, JsonNode> verdicts = new HashMap<>();
        geminiClient.generateJson(solvePrompt(task, context, drafts)).ifPresent(answer ->
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

    private static String solvePrompt(String task, String context, List<Draft> drafts) {
        StringBuilder questions = new StringBuilder();
        if (context != null) {
            questions.append(context).append("\n\n");
        }
        for (int index = 0; index < drafts.size(); index++) {
            Draft draft = drafts.get(index);
            List<String> options = options(draft.question());
            questions.append("Câu ").append(index + 1).append(": ").append(draft.prompt()).append('\n');
            for (int option = 0; option < 4; option++) {
                questions.append("  ").append(option + 1).append(") ").append(options.get(option)).append('\n');
            }
        }
        return """
                Bạn là thí sinh rất giỏi tiếng Nhật đang làm đề JLPT. %s Xét kỹ từng lựa chọn: có lựa chọn nào khác \
                cũng chấp nhận được không (về ngữ pháp và nghĩa)?
                %s
                Trả về một mảng JSON, không thêm gì khác: [{"index": số thứ tự câu, "answer": số thứ tự lựa chọn đúng \
                nhất (1-4), "alsoCorrect": [số thứ tự các lựa chọn khác cũng chấp nhận được, có thể rỗng], "note": \
                "lý do ngắn bằng tiếng Việt"}]
                """.formatted(task, questions);
    }

    /** Một câu chỉ giữ một cờ: sai đáp án nặng nhất, rồi tới hai đáp án, rồi tới từ vượt cấp. */
    static void flag(ExamQuestion question, ExamQuestionFlag flag, String detail) {
        if (question.getFlag() == null || flag.severity() < question.getFlag().severity()) {
            question.setFlag(flag);
        }
        note(question, detail);
    }

    static void note(ExamQuestion question, String detail) {
        question.setReviewNote(question.getReviewNote() == null ? detail : question.getReviewNote() + " " + detail);
    }

    static List<String> options(ExamQuestion question) {
        return List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD());
    }

    static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asText("").strip()));
        }
        return values;
    }

    /** Chữ cái A-D của lựa chọn thứ {@code index} (0-3). */
    static String letter(int index) {
        return String.valueOf(LETTERS.charAt(index));
    }

    /** Đủ 4 lựa chọn vừa cột trong bảng, đáp án là một trong 4 vị trí. */
    static boolean storable(List<String> options, int answer) {
        return options.size() == 4 && answer >= 0 && answer <= 3
                && options.stream().allMatch(option -> option.length() <= ExamQuestionValidator.MAX_OPTION_LENGTH);
    }

    /** Câu nháp AI chờ duyệt; xáo 4 lựa chọn, đáp án theo vị trí mới của {@code correct}. */
    static ExamQuestion question(JlptLevel level, JlptQuestionType type, String text, String sentence, String highlight,
                                 List<String> options, String correct, String explanation) {
        List<String> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled);
        String trimmed = explanation.strip();
        return ExamQuestion.builder()
                .jlptLevel(level)
                .questionText(text)
                .sentence(sentence)
                .highlight(highlight)
                .optionA(shuffled.get(0))
                .optionB(shuffled.get(1))
                .optionC(shuffled.get(2))
                .optionD(shuffled.get(3))
                .correctOption(letter(shuffled.indexOf(correct)))
                .explanation(trimmed.isEmpty() ? null : trimmed)
                .questionType(type)
                .status(ExamQuestionStatus.DRAFT)
                .source(ExamQuestionSource.AI)
                .build();
    }
}
