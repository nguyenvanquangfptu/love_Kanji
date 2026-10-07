package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.service.DraftReviewer.Draft;
import com.kanjimastery.backend.service.WordClassifier.PartOfSpeech;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Nhờ AI (Gemini) viết nháp câu thi: câu ngữ pháp (文法形式の判断, 文の組み立て) cho một điểm ngữ pháp, và câu từ vựng
 * (言い換え類義, 用法) cho các từ trong bài của một cấp độ chưa có câu dạng đó. Câu nháp qua các bước kiểm tra của
 * {@link DraftReviewer} (cấu trúc - sai thì loại luôn, kèm lý do; từ vượt cấp; AI giải lại) rồi vào hàng chờ duyệt, có
 * cảnh báo thì gắn cờ. Mỗi lần sinh tốn hai request Gemini (viết + giải lại).
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
    /** 言い換え類義 từ N3: thay phần gạch chân bằng từ, cách nói khác. */
    static final String PARAPHRASE_PART_TEXT = "Chọn từ hoặc cách nói gần nghĩa nhất với phần được gạch chân.";
    /** 言い換え類義 của N4, N5: cả câu được gạch chân, chọn câu gần nghĩa nhất. */
    static final String PARAPHRASE_SENTENCE_TEXT = "Chọn câu gần nghĩa nhất với câu được gạch chân.";
    private static final Set<JlptQuestionType> GRAMMAR_TYPES = EnumSet.of(JlptQuestionType.GRAMMAR_FORM,
            JlptQuestionType.SENTENCE_ORDER);
    private static final Set<JlptQuestionType> VOCABULARY_TYPES =
            EnumSet.of(JlptQuestionType.PARAPHRASE, JlptQuestionType.USAGE);
    /** Các cấp độ mà 言い換え類義 gạch chân cả câu. */
    private static final Set<JlptLevel> WHOLE_SENTENCE_PARAPHRASE = EnumSet.of(JlptLevel.N4, JlptLevel.N5);
    /** Từ loại hỏi được 言い換え, 用法 - không hỏi liên từ, từ chỉ định, câu chào... */
    private static final Set<PartOfSpeech> VOCABULARY_CLASSES = EnumSet.of(PartOfSpeech.NOUN, PartOfSpeech.VERB,
            PartOfSpeech.I_ADJECTIVE, PartOfSpeech.NA_ADJECTIVE, PartOfSpeech.ADVERB);
    /** Mục từ là cả một cụm (có dấu cách, dấu câu) thì không hỏi được. */
    private static final Pattern PHRASE = Pattern.compile("[\\s、。,.!?！？〜~]");
    /** Ô trống AI hay viết lệch: （ ）, (　　), （　）... */
    private static final Pattern LOOSE_BLANK = Pattern.compile("[（(][\\s　]*[）)]");

    private final GeminiClient geminiClient;
    private final DraftReviewer reviewer;
    private final GrammarPointRepository grammarPointRepository;
    private final ExamQuestionRepository questionRepository;
    private final KanjiRepository kanjiRepository;
    private final JlptBlueprintProperties blueprints;

    /**
     * @param drafted    số câu vào hàng chờ duyệt (kể cả câu có cảnh báo)
     * @param flagged    trong đó, số câu có cảnh báo
     * @param rejected   số câu sai cấu trúc, bị loại luôn
     * @param unreadable số mục AI trả về không đọc được, bỏ qua
     */
    public record DraftResult(int drafted, int flagged, int rejected, int unreadable) {
    }

    /** Câu ngữ pháp 文法形式の判断 hoặc 文の組み立て cho một điểm ngữ pháp. */
    public DraftResult draft(Long grammarPointId, JlptQuestionType type, int count) {
        requireGemini();
        if (!GRAMMAR_TYPES.contains(type)) {
            throw new BadRequestException("Chỉ sinh nháp được dạng 文法形式の判断 và 文の組み立て");
        }
        GrammarPoint point = grammarPointRepository.findById(grammarPointId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm ngữ pháp: " + grammarPointId));
        List<String> existing = questionRepository.findSentencesByGrammarPoint(grammarPointId);

        String answer = ask(grammarPrompt(point, type, limit(count), existing));
        List<Draft> drafts = new ArrayList<>();
        int unreadable = 0;
        for (JsonNode item : reviewer.items(answer)) {
            Optional<Draft> draft = JlptQuestionType.GRAMMAR_FORM.equals(type) ? grammarForm(item, point)
                    : sentenceOrder(item, point);
            if (draft.isPresent()) {
                drafts.add(draft.get());
            } else {
                unreadable++;
            }
        }
        String task = JlptQuestionType.GRAMMAR_FORM.equals(type)
                ? "Mỗi câu chọn lựa chọn điền vào chỗ trống " + ExamQuestionGenerator.BLANK + "."
                : "Mỗi câu sắp xếp 4 vế vào 4 ô " + ExamQuestionValidator.ORDER_SLOT
                + " cho thành câu đúng, rồi cho biết vế nào nằm ở ô ★.";
        return review(point.getJlptLevel(), task, drafts, unreadable,
                type + " cho 「" + point.getPattern() + "」 (" + point.getJlptLevel() + ")");
    }

    /**
     * Câu từ vựng 言い換え類義 hoặc 用法 (dạng câu phải có trong đề của cấp độ) cho các từ trong bài của cấp độ chưa có
     * câu dạng đó, mỗi từ một câu.
     */
    public DraftResult draftVocabulary(String level, JlptQuestionType type, int count) {
        requireGemini();
        JlptLevel normalized = Levels.require(level);
        if (!VOCABULARY_TYPES.contains(type)) {
            throw new BadRequestException("Chỉ sinh nháp từ vựng được dạng 言い換え類義 và 用法");
        }
        boolean inExam = blueprints.section(normalized, ExamSection.VOCABULARY)
                .map(section -> section.getQuestions().containsKey(type))
                .orElse(false);
        if (!inExam) {
            throw new BadRequestException("Đề " + normalized + " không có dạng câu này");
        }
        List<Kanji> words = wordsWithoutQuestion(normalized, type, limit(count));
        if (words.isEmpty()) {
            throw new BadRequestException("Các từ trong bài " + normalized + " đều đã có câu dạng này");
        }

        boolean wholeSentence = WHOLE_SENTENCE_PARAPHRASE.contains(normalized);
        String answer = ask(vocabularyPrompt(normalized, type, wholeSentence, words));
        List<Draft> drafts = new ArrayList<>();
        int unreadable = 0;
        for (JsonNode item : reviewer.items(answer)) {
            Optional<Draft> draft = JlptQuestionType.USAGE.equals(type) ? usage(item, normalized, words)
                    : paraphrase(item, normalized, wholeSentence, words);
            if (draft.isPresent()) {
                drafts.add(draft.get());
            } else {
                unreadable++;
            }
        }
        String task = switch (type) {
            case JlptQuestionType.USAGE ->
                    "Mỗi câu chọn câu dùng từ đã cho đúng nhất (đúng nghĩa, đúng cách kết hợp từ).";
            default -> wholeSentence ? "Mỗi câu chọn câu có nghĩa gần nhất với câu đã cho."
                    : "Mỗi câu chọn từ hoặc cách nói gần nghĩa nhất với phần được gạch chân.";
        };
        return review(normalized, task, drafts, unreadable, type + " " + normalized);
    }

    private void requireGemini() {
        if (!geminiClient.isEnabled()) {
            throw new BadRequestException("Chưa cấu hình Gemini (GEMINI_API_KEY) nên chưa sinh nháp được");
        }
    }

    private static int limit(int count) {
        return Math.max(1, Math.min(count, MAX_DRAFTS));
    }

    private String ask(String prompt) {
        return geminiClient.generateJson(prompt).orElseThrow(() -> new BadRequestException(
                "Gemini không trả lời (hết hạn mức trong ngày hoặc lỗi mạng) - thử lại sau"));
    }

    /** Kiểm tra các câu nháp (câu đã bị loại từ trước thì bỏ qua), lưu tất cả, kể cả câu bị loại. */
    private DraftResult review(JlptLevel level, String task, List<Draft> drafts, int unreadable, String subject) {
        List<Draft> candidates = drafts.stream()
                .filter(draft -> !ExamQuestionStatus.REJECTED.equals(draft.question().getStatus()))
                .toList();
        List<Draft> wellFormed = reviewer.keepWellFormed(candidates);
        reviewer.checkLevel(level, wellFormed);
        reviewer.checkBySolvingAgain(task, null, wellFormed);

        questionRepository.saveAll(drafts.stream().map(Draft::question).toList());
        int flagged = (int) wellFormed.stream().filter(draft -> draft.question().getFlag() != null).count();
        int rejected = drafts.size() - wellFormed.size();
        log.info("Sinh nháp {}: {} chờ duyệt ({} có cảnh báo), {} bị loại, {} không đọc được", subject,
                wellFormed.size(), flagged, rejected, unreadable);
        return new DraftResult(wellFormed.size(), flagged, rejected, unreadable);
    }

    private static String grammarPrompt(GrammarPoint point, JlptQuestionType type, int count, List<String> existing) {
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

    private static Optional<Draft> grammarForm(JsonNode item, GrammarPoint point) {
        String sentence = LOOSE_BLANK.matcher(item.path("sentence").asText("").strip())
                .replaceAll(ExamQuestionGenerator.BLANK);
        List<String> options = DraftReviewer.texts(item.path("options"));
        int answer = item.path("answer").asInt(-1);
        if (sentence.isEmpty() || !DraftReviewer.storable(options, answer)) {
            return Optional.empty();
        }
        String correct = options.get(answer);
        ExamQuestion question = DraftReviewer.question(point.getJlptLevel(), JlptQuestionType.GRAMMAR_FORM,
                GRAMMAR_FORM_TEXT, sentence, null, options, correct, item.path("explanation").asText(""));
        question.getGrammarPointIds().add(point.getId());
        return Optional.of(new Draft(question, sentence.replace(ExamQuestionGenerator.BLANK, correct), sentence));
    }

    private static Optional<Draft> sentenceOrder(JsonNode item, GrammarPoint point) {
        String before = item.path("before").asText("").strip();
        String after = item.path("after").asText("").strip();
        List<String> parts = DraftReviewer.texts(item.path("parts"));
        int star = item.path("star").asInt(3);
        if (!DraftReviewer.storable(parts, star - 1)) {
            return Optional.empty();
        }
        String slots = IntStream.rangeClosed(1, 4)
                .mapToObj(slot -> slot == star ? ExamQuestionValidator.ORDER_STAR : ExamQuestionValidator.ORDER_SLOT)
                .collect(Collectors.joining(" "));
        String sentence = (before + " " + slots + " " + after).strip();
        String full = before + String.join("", parts) + after;
        String correct = parts.get(star - 1);
        String explanation = (item.path("explanation").asText("").strip() + " Câu đúng: " + full + " ("
                + String.join(" → ", parts) + "). Vế ở vị trí ★ là 「" + correct + "」.").strip();
        ExamQuestion question = DraftReviewer.question(point.getJlptLevel(), JlptQuestionType.SENTENCE_ORDER,
                SENTENCE_ORDER_TEXT, sentence, null, parts, correct, explanation);
        question.getGrammarPointIds().add(point.getId());
        return Optional.of(new Draft(question, full, sentence));
    }

    /** Từ trong bài của cấp độ chưa có câu dạng {@code type}, có nghĩa, đúng từ loại hỏi được; chọn ngẫu nhiên. */
    private List<Kanji> wordsWithoutQuestion(JlptLevel level, JlptQuestionType type, int count) {
        Set<Long> covered = new HashSet<>(questionRepository.findWordIdsWithQuestion(level.name(), type.name()));
        List<Kanji> candidates = kanjiRepository.findAllByTagNamePrefix(level + "-%").stream()
                .filter(word -> !covered.contains(word.getId()))
                .filter(word -> StringUtils.hasText(word.getMeaning()))
                .filter(word -> !PHRASE.matcher(word.getCharacter()).find())
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        List<Kanji> words = new ArrayList<>();
        if (candidates.isEmpty()) {
            return words;
        }
        WordClassifier classifier = new WordClassifier();
        for (Kanji word : candidates) {
            if (words.size() >= count) {
                break;
            }
            if (VOCABULARY_CLASSES.contains(classifier.classify(word.getCharacter()))) {
                words.add(word);
            }
        }
        return words;
    }

    private static String vocabularyPrompt(JlptLevel level, JlptQuestionType type, boolean wholeSentence, List<Kanji> words) {
        String list = IntStream.range(0, words.size())
                .mapToObj(index -> (index + 1) + ". " + words.get(index).getCharacter()
                        + readingInBrackets(words.get(index)) + " - " + words.get(index).getMeaning())
                .collect(Collectors.joining("\n"));
        String common = """
                Bạn là giáo viên tiếng Nhật đang soạn câu hỏi luyện thi JLPT %s.
                Viết cho MỖI từ dưới đây đúng một câu hỏi MỚI do bạn tự nghĩ - không chép đề thi thật, sách hay trang \
                web nào. Viết từ đúng như trong danh sách (được chia dạng khi cần). Chỉ dùng từ vựng và chữ Hán tới \
                trình độ %s. Mỗi câu chỉ có MỘT đáp án đúng; không lựa chọn nào khác cũng chấp nhận được.
                Các từ (số thứ tự. từ (cách đọc) - nghĩa):
                %s
                """.formatted(level, level, list);
        if (JlptQuestionType.USAGE.equals(type)) {
            return common + """
                    Dạng 問題5 用法: chọn câu dùng từ đúng nhất.
                    - "index": số thứ tự của từ trong danh sách.
                    - "options": đúng 4 câu ngắn, câu nào cũng có từ đó: 1 câu dùng đúng nghĩa và đúng cách kết hợp \
                    từ; 3 câu dùng từ sai mà người Nhật thấy sai rõ ràng (nhầm nghĩa với từ khác, sai kết hợp \
                    từ) - không phải lỗi ngữ pháp.
                    - "answer": vị trí (0-3) của câu đúng trong "options".
                    - "explanation": giải thích ngắn bằng tiếng Việt: nghĩa của từ, và mỗi câu sai lẽ ra nên dùng từ gì.
                    Trả về một mảng JSON, không thêm gì khác:
                    [{"index": 1, "options": ["...", "...", "...", "..."], "answer": 0, "explanation": "..."}]
                    """;
        }
        if (wholeSentence) {
            return common + """
                    Dạng 問題4 言い換え類義 (kiểu N4, N5): một câu ngắn có dùng từ đó, cả câu được gạch chân; chọn câu có \
                    nghĩa gần nhất với câu đó.
                    - "index": số thứ tự của từ trong danh sách.
                    - "sentence": câu ngắn có dùng từ.
                    - "options": đúng 4 câu: 1 câu cùng nghĩa với "sentence" nhưng nói bằng từ khác (không dùng lại từ \
                    đang hỏi); 3 câu giống về hình thức nhưng nghĩa khác rõ ràng.
                    - "answer": vị trí (0-3) của câu đúng trong "options".
                    - "explanation": giải thích ngắn bằng tiếng Việt.
                    Trả về một mảng JSON, không thêm gì khác:
                    [{"index": 1, "sentence": "...", "options": ["...", "...", "...", "..."], "answer": 0, \
                    "explanation": "..."}]
                    """;
        }
        return common + """
                Dạng 問題4 言い換え類義: một câu có dùng từ đó, phần chứa từ được gạch chân; chọn từ hoặc cách nói gần \
                nghĩa nhất với phần gạch chân.
                - "index": số thứ tự của từ trong danh sách.
                - "sentence": câu có dùng từ.
                - "highlight": phần được gạch chân - chép nguyên văn một đoạn liền của "sentence" có chứa từ (thường \
                là chính từ đó cùng đuôi chia).
                - "options": đúng 4 từ hoặc cách nói thay được vào chỗ phần gạch chân mà câu vẫn đúng ngữ pháp (cùng \
                dạng chia): 1 lựa chọn cùng nghĩa với phần gạch chân, 3 lựa chọn nghĩa khác hẳn.
                - "answer": vị trí (0-3) của lựa chọn đúng trong "options".
                - "explanation": giải thích ngắn bằng tiếng Việt.
                Trả về một mảng JSON, không thêm gì khác:
                [{"index": 1, "sentence": "...", "highlight": "...", "options": ["...", "...", "...", "..."], \
                "answer": 0, "explanation": "..."}]
                """;
    }

    private static Optional<Draft> paraphrase(JsonNode item, JlptLevel level, boolean wholeSentence, List<Kanji> words) {
        Optional<Kanji> word = word(item, words);
        String sentence = item.path("sentence").asText("").strip();
        List<String> options = DraftReviewer.texts(item.path("options"));
        int answer = item.path("answer").asInt(-1);
        String highlight = wholeSentence ? sentence : item.path("highlight").asText("").strip();
        if (word.isEmpty() || sentence.isEmpty() || !DraftReviewer.storable(options, answer)
                || highlight.length() > ExamQuestionValidator.MAX_HIGHLIGHT_LENGTH) {
            return Optional.empty();
        }
        ExamQuestion question = DraftReviewer.question(level, JlptQuestionType.PARAPHRASE,
                wholeSentence ? PARAPHRASE_SENTENCE_TEXT : PARAPHRASE_PART_TEXT, sentence, highlight, options,
                options.get(answer), item.path("explanation").asText(""));
        question.getKanjiIds().add(word.get().getId());
        if (!mentions(highlight, word.get())) {
            reject(question, "phần gạch chân không có từ 「" + word.get().getCharacter() + "」");
        }
        String prompt = wholeSentence ? sentence : sentence + " (phần gạch chân: 「" + highlight + "」)";
        return Optional.of(new Draft(question, sentence + "\n" + String.join("\n", options), prompt));
    }

    private static Optional<Draft> usage(JsonNode item, JlptLevel level, List<Kanji> words) {
        Optional<Kanji> word = word(item, words);
        List<String> options = DraftReviewer.texts(item.path("options"));
        int answer = item.path("answer").asInt(-1);
        if (word.isEmpty() || !DraftReviewer.storable(options, answer)) {
            return Optional.empty();
        }
        String character = word.get().getCharacter();
        ExamQuestion question = DraftReviewer.question(level, JlptQuestionType.USAGE,
                "Chọn câu dùng từ 「" + character + "」 đúng nhất.", null, null, options, options.get(answer),
                item.path("explanation").asText(""));
        question.getKanjiIds().add(word.get().getId());
        if (!options.stream().allMatch(option -> mentions(option, word.get()))) {
            reject(question, "có câu không dùng từ 「" + character + "」");
        }
        return Optional.of(new Draft(question, String.join("\n", options), "Từ 「" + character + "」"));
    }

    /** Từ trong danh sách mà mục AI trả về đang hỏi ("index" 1..n). */
    private static Optional<Kanji> word(JsonNode item, List<Kanji> words) {
        int index = item.path("index").asInt(0);
        return index >= 1 && index <= words.size() ? Optional.of(words.get(index - 1)) : Optional.empty();
    }

    /**
     * {@code text} có dùng từ (kể cả dạng chia, hoặc viết bằng hiragana): so phần gốc của từ - bỏ đuôi する và chữ
     * hiragana cuối. Cách đọc chỉ dùng khi phần gốc đủ dài (「み」 của 見る thì chữ nào cũng khớp).
     */
    static boolean mentions(String text, Kanji word) {
        if (text.contains(stem(word.getCharacter()))) {
            return true;
        }
        String reading = word.getReading() == null ? "" : stem(word.getReading());
        return reading.length() >= 2 && text.contains(reading);
    }

    static String stem(String word) {
        String plain = word.replaceAll("[()（）]", "").strip();
        if (plain.length() > 2 && plain.endsWith("する")) {
            return plain.substring(0, plain.length() - 2);
        }
        boolean endsInHiragana = plain.length() > 1
                && Character.UnicodeBlock.of(plain.charAt(plain.length() - 1)) == Character.UnicodeBlock.HIRAGANA;
        return endsInHiragana ? plain.substring(0, plain.length() - 1) : plain;
    }

    private static String readingInBrackets(Kanji word) {
        boolean showsReading = StringUtils.hasText(word.getReading()) && !word.getReading().equals(word.getCharacter());
        return showsReading ? " (" + word.getReading() + ")" : "";
    }

    private static void reject(ExamQuestion question, String problem) {
        question.setStatus(ExamQuestionStatus.REJECTED);
        DraftReviewer.note(question, "Loại tự động - " + problem + ".");
    }
}
