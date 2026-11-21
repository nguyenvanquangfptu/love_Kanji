package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamImportRequest;
import com.kanjimastery.backend.dto.ExamImportRequest.Item;
import com.kanjimastery.backend.dto.ExamImportResponse;
import com.kanjimastery.backend.dto.ExamPassageImportRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Nhập một đề tự soạn ({@link ExamImportRequest}, phần Từ vựng, Ngữ pháp và Đọc hiểu) vào kho câu thi: mỗi câu thành
 * một câu thi đúng dạng 問題 của đề thật, đoạn văn 文章の文法 và mỗi bài đọc thành một đoạn văn kèm các câu hỏi của nó.
 * Cũng nhập được một bài đọc lẻ ({@link ExamPassageImportRequest}). Câu vào kho ở trạng thái DRAFT, nguồn IMPORTED,
 * chờ duyệt ở trang quản trị; thứ tự lựa chọn giữ như đề gốc.
 * <p>
 * Kiểm tra hết trước khi ghi: mỗi 問題 đủ đúng số câu (số bài) theo cấu trúc đề, số câu đánh liên tục, câu nào sai cấu
 * trúc (thiếu phần gạch chân, ★ không khớp đáp án...) thì báo lỗi và không ghi gì - sửa file rồi nhập lại. Câu đã nhập
 * ở lần trước (cùng mã câu đề gốc, vd. N3-05/NP/15) được bỏ qua, nên nhập lại một đề không tạo câu trùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamImportService {

    static final String VOCABULARY_PART = "TV";
    static final String GRAMMAR_PART = "NP";
    static final String READING_PART = "DH";
    static final String KANJI_READING_TEXT = "Chọn cách đọc đúng của từ được gạch chân.";
    static final String ORTHOGRAPHY_TEXT = "Chọn cách viết bằng chữ Hán của từ được gạch chân.";
    static final String CONTEXT_TEXT = ExamQuestionGenerator.BLANK + "に 入る ことばを えらんで ください。";
    private static final Pattern TEST_CODE = Pattern.compile("[A-Za-z0-9-]{1,30}");
    /** Số thứ tự đứng đầu lựa chọn khi chép từ đề giấy: "1 こわして", "2. ...", "３、...". */
    private static final Pattern OPTION_NUMBER = Pattern.compile("^([1-4１-４])[\\s　.．、)）]+");
    /** Đuôi okurigana của cách viết: 許す -> 許 (cột character của động từ chỉ có phần chữ Hán). */
    private static final Pattern TRAILING_HIRAGANA = Pattern.compile("\\p{IsHiragana}+$");
    private static final Set<JlptQuestionType> GRAMMAR_TYPES = Set.of(JlptQuestionType.GRAMMAR_FORM,
            JlptQuestionType.SENTENCE_ORDER, JlptQuestionType.TEXT_GRAMMAR);
    private static final int MAX_TITLE_LENGTH = 200;
    /** Số câu nêu tên trong cảnh báo câu chưa có giải thích. */
    private static final int MISSING_EXPLANATION_EXAMPLES = 5;
    /**
     * Độ giống ({@link TextSimilarity}) từ đó báo câu / đoạn văn gần trùng. Dò trên kho N3 (11/2026): mọi cặp câu từ 0,6
     * trở lên đều là câu chép lại có đổi vài chữ (「国によって、食事の習慣が異なる / 違う」 0,67; 「子どものころは、この川で
     * よく泳いだものです / ものだ」 0,59 - đảo trật tự từ làm điểm tụt nhanh). Bài đọc dài nên hai bài khác nhau vẫn chung
     * nhiều cặp ký tự (です, ている...), nhưng trong kho chưa có cặp nào quá 0,4.
     */
    static final double SIMILAR_QUESTION = 0.6;
    static final double SIMILAR_PASSAGE = 0.5;
    /** Số ký tự trích từ câu / đoạn văn trong kho trong cảnh báo. */
    private static final int QUOTE_LENGTH = 40;
    /** Câu đúng của câu sắp xếp trong giải thích, xem {@code ParsedTest.order} và {@code QuestionDraftService}. */
    private static final Pattern ORDER_ANSWER = Pattern.compile("Câu đúng: (.+?) \\(");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ExamQuestionRepository questionRepository;
    private final ExamPassageRepository passageRepository;
    private final KanjiRepository kanjiRepository;
    private final GrammarPointRepository grammarPointRepository;
    private final JlptBlueprintProperties blueprints;
    private final Clock clock;

    /**
     * Kiểm tra rồi ghi một đề vào kho; {@code dryRun} thì chỉ kiểm tra. Có lỗi thì không ghi gì.
     */
    @Transactional
    public ExamImportResponse importTest(ExamImportRequest request, boolean dryRun) {
        String code = request.testCode() == null ? "" : request.testCode().strip();
        if (!TEST_CODE.matcher(code).matches()) {
            throw new BadRequestException("Mã đề (\"de\") chỉ gồm chữ, số và dấu gạch ngang, tối đa 30 ký tự - vd. N3-05");
        }
        JlptLevel level = Levels.require(request.level());
        if (request.vocabulary() == null && request.grammar() == null && request.reading() == null) {
            throw new BadRequestException("Đề chưa có phần nào: cần \"tu_vung\", \"ngu_phap\" và/hoặc \"doc_hieu\"");
        }

        ParsedTest test = new ParsedTest(code, level, LocalDateTime.now(clock));
        if (request.vocabulary() != null) {
            ExamImportRequest.VocabularyPart part = request.vocabulary();
            Map<JlptQuestionType, List<Item>> items = new EnumMap<>(JlptQuestionType.class);
            items.put(JlptQuestionType.KANJI_READING, orEmpty(part.kanjiReading()));
            items.put(JlptQuestionType.ORTHOGRAPHY, orEmpty(part.orthography()));
            items.put(JlptQuestionType.CONTEXT, orEmpty(part.context()));
            items.put(JlptQuestionType.PARAPHRASE, orEmpty(part.paraphrase()));
            items.put(JlptQuestionType.USAGE, orEmpty(part.usage()));
            test.section(blueprint(level, ExamSection.VOCABULARY), VOCABULARY_PART, items, null);
        }
        if (request.grammar() != null) {
            ExamImportRequest.GrammarPart part = request.grammar();
            Map<JlptQuestionType, List<Item>> items = new EnumMap<>(JlptQuestionType.class);
            items.put(JlptQuestionType.GRAMMAR_FORM, orEmpty(part.grammarForm()));
            items.put(JlptQuestionType.SENTENCE_ORDER, orEmpty(part.sentenceOrder()));
            test.section(blueprint(level, ExamSection.GRAMMAR), GRAMMAR_PART, items, part.passage());
        }
        if (request.reading() != null) {
            ExamImportRequest.ReadingPart part = request.reading();
            Map<JlptQuestionType, List<ExamImportRequest.Passage>> passages = new EnumMap<>(JlptQuestionType.class);
            passages.put(JlptQuestionType.READING_SHORT, orEmpty(part.shortPassages()));
            passages.put(JlptQuestionType.READING_MID, orEmpty(part.midPassages()));
            passages.put(JlptQuestionType.READING_LONG, orEmpty(part.longPassages()));
            passages.put(JlptQuestionType.INFO_SEARCH, orEmpty(part.infoSearch()));
            test.reading(blueprint(level, ExamSection.READING), passages,
                    blueprints.firstQuestionNumber(level, ExamSection.READING),
                    blueprints.firstMondaiNumber(level, ExamSection.READING));
        }
        return save(test, dryRun);
    }

    /**
     * Kiểm tra rồi ghi một bài đọc lẻ dạng {@code type} của phần Đọc hiểu; {@code dryRun} thì chỉ kiểm tra. Các câu hỏi
     * đánh số 1..n. Có mã bài ("ma") thì nhập lại không tạo bài trùng; không có mã thì bài cùng nội dung đã có trong kho
     * được bỏ qua.
     */
    @Transactional
    public ExamImportResponse importPassage(JlptQuestionType type, ExamPassageImportRequest request, boolean dryRun) {
        if (type == null || type.section() != ExamSection.READING) {
            throw new BadRequestException("Dạng bài phải là một dạng của phần Đọc hiểu: READING_SHORT, READING_MID, "
                    + "READING_LONG hoặc INFO_SEARCH");
        }
        String code = request.code() == null ? null : request.code().strip();
        if (code != null && !code.isEmpty() && !TEST_CODE.matcher(code).matches()) {
            throw new BadRequestException("Mã bài (\"ma\") chỉ gồm chữ, số và dấu gạch ngang, tối đa 30 ký tự");
        }
        JlptLevel level = Levels.require(request.level());
        JlptBlueprintProperties.Section section = blueprint(level, ExamSection.READING);
        if (!section.getQuestions().containsKey(type)) {
            throw new BadRequestException("Đề " + level + " không có dạng bài " + type);
        }
        ParsedTest test = new ParsedTest(code == null || code.isEmpty() ? null : code, level, LocalDateTime.now(clock));
        test.readingPassage(type, section.questionsPerPassage(type).orElse(null),
                new ExamImportRequest.Passage(request.title(), request.content(), request.questions()), 1);
        return save(test, dryRun);
    }

    /**
     * Gắn từ, ngữ pháp, cảnh báo, bỏ câu và đoạn văn đã nhập ở lần trước, rồi ghi (trừ khi {@code dryRun} hoặc có lỗi).
     * Đoạn văn đi cùng các câu hỏi của nó: đoạn đã có thì các câu của nó cũng không ghi lại.
     */
    private ExamImportResponse save(ParsedTest test, boolean dryRun) {
        List<String> warnings = new ArrayList<>();
        linkWords(test.questions, warnings);
        linkGrammar(test.level, test.questions, warnings);
        warnMissingExplanations(test.questions, warnings);

        List<String> refs = test.questions.stream().map(Built::ref).filter(Objects::nonNull).toList();
        Set<String> existing = refs.isEmpty() ? Set.of() : new HashSet<>(questionRepository.findExistingSourceRefs(refs));
        List<ExamPassage> newPassages = test.passages.stream()
                .map(BuiltPassage::passage)
                .filter(passage -> passage.getSourceRef() != null
                        ? !passageRepository.existsBySourceRef(passage.getSourceRef())
                        : !passageRepository.existsByJlptLevelAndSectionAndContent(passage.getJlptLevel(),
                                passage.getSection(), passage.getContent()))
                .toList();
        List<Built> fresh = test.questions.stream()
                .filter(built -> built.ref() == null || !existing.contains(built.ref()))
                .filter(built -> built.passage() == null || newPassages.contains(built.passage()))
                .toList();
        warnSimilarQuestions(test.level, test.questions, fresh, warnings);
        warnSimilarPassages(test.level, test.passages, newPassages, warnings);

        String code = test.code;
        int alreadyImported = test.questions.size() - fresh.size();
        if (!test.errors.isEmpty() || dryRun) {
            return new ExamImportResponse(code, dryRun, false, fresh.size(), newPassages.size(), alreadyImported,
                    test.errors, warnings);
        }
        for (ExamPassage passage : newPassages) {
            ExamPassage stored = passageRepository.save(passage);
            fresh.stream()
                    .filter(built -> built.passage() == passage)
                    .forEach(built -> built.question().setPassageId(stored.getId()));
        }
        questionRepository.saveAll(fresh.stream().map(Built::question).toList());
        log.info("Nhập đề {} ({}): {} câu, {} đoạn văn, bỏ qua {} câu đã có", code, test.level, fresh.size(),
                newPassages.size(), alreadyImported);
        return new ExamImportResponse(code, false, true, fresh.size(), newPassages.size(), alreadyImported, test.errors,
                warnings);
    }

    private JlptBlueprintProperties.Section blueprint(JlptLevel level, ExamSection section) {
        return blueprints.section(level, section).orElseThrow(() -> new BadRequestException(
                "Chưa có cấu trúc đề " + level + " cho phần " + section.vietnameseName()));
    }

    /**
     * Gắn câu từ vựng với từ trong kho (làm sai thì từ được đưa vào ôn tập; một buổi thi không hỏi một từ hai lần):
     * theo trường "tu" nếu có, không thì theo phần gạch chân / đáp án đúng / từ khoá. Kho có nhiều từ cùng chữ thì chọn
     * từ có cách đọc khớp, nếu biết cách đọc. Không tìm thấy thì chỉ cảnh báo.
     */
    private void linkWords(List<Built> questions, List<String> warnings) {
        Set<String> keys = new HashSet<>();
        for (Built built : questions) {
            if (built.word() != null) {
                keys.add(built.word().written());
                keys.add(stem(built.word().written()));
            }
        }
        keys.remove("");
        if (keys.isEmpty()) {
            return;
        }
        Map<String, List<Kanji>> byCharacter = kanjiRepository.findAllByCharacterIn(keys).stream()
                .collect(Collectors.groupingBy(Kanji::getCharacter));
        for (Built built : questions) {
            WordHint word = built.word();
            if (word == null) {
                continue;
            }
            Set<Kanji> candidates = new HashSet<>(byCharacter.getOrDefault(word.written(), List.of()));
            byCharacter.getOrDefault(stem(word.written()), List.of()).stream()
                    .filter(kanji -> word.written().equals(ReadingMatcher.writtenForm(kanji.getCharacter(), kanji.getReading())))
                    .forEach(candidates::add);
            List<Kanji> matches = new ArrayList<>(candidates);
            if (word.reading() != null) {
                List<Kanji> sameReading = matches.stream()
                        .filter(kanji -> kanji.getReading() != null
                                && word.reading().equals(ReadingMatcher.displayReading(kanji.getReading())))
                        .toList();
                if (!sameReading.isEmpty()) {
                    matches = sameReading;
                }
            }
            if (matches.isEmpty()) {
                warnings.add(built.label() + ": chưa gắn được với từ vựng nào (「" + word.written()
                        + "」 không có trong kho từ) - thêm \"tu\" (dạng từ điển) nếu muốn câu làm sai được đưa vào ôn tập");
            } else {
                matches.forEach(kanji -> built.question().getKanjiIds().add(kanji.getId()));
            }
        }
    }

    /** Gắn câu ngữ pháp với điểm ngữ pháp theo trường "ngu_phap" (cùng cấp độ trước, rồi tới cấp độ khác). */
    private void linkGrammar(JlptLevel level, List<Built> questions, List<String> warnings) {
        int withoutPattern = 0;
        for (Built built : questions) {
            if (!GRAMMAR_TYPES.contains(built.question().getQuestionType())) {
                continue;
            }
            if (built.grammarPattern() == null) {
                withoutPattern++;
                continue;
            }
            String pattern = GrammarPointService.normalizePattern(built.grammarPattern());
            Optional<GrammarPoint> point = grammarPointRepository.findByJlptLevelAndPattern(level, pattern)
                    .or(() -> grammarPointRepository.findByPattern(pattern).stream().findFirst());
            if (point.isPresent()) {
                built.question().getGrammarPointIds().add(point.get().getId());
            } else {
                warnings.add(built.label() + ": không có mẫu 「" + pattern + "」 trong danh sách ngữ pháp");
            }
        }
        if (withoutPattern > 0) {
            warnings.add(withoutPattern + " câu ngữ pháp chưa ghi \"ngu_phap\" nên chưa gắn với điểm ngữ pháp nào"
                    + " - thêm vào nếu muốn dùng cho phần luyện ngữ pháp yếu");
        }
    }

    /**
     * Câu chưa có giải thích ("giai_thich"; câu sắp xếp có sẵn phần dựng từ thứ tự đúng): người học xem lại bài sẽ không
     * biết vì sao mình sai. Chỉ cảnh báo, kèm vài câu đầu tiên để dễ tìm.
     */
    private static void warnMissingExplanations(List<Built> questions, List<String> warnings) {
        List<String> labels = questions.stream()
                .filter(built -> !StringUtils.hasText(built.question().getExplanation()))
                .map(Built::label)
                .toList();
        if (labels.isEmpty()) {
            return;
        }
        String examples = String.join(", ", labels.subList(0, Math.min(labels.size(), MISSING_EXPLANATION_EXAMPLES)))
                + (labels.size() > MISSING_EXPLANATION_EXAMPLES ? "..." : "");
        warnings.add(labels.size() + " câu chưa có \"giai_thich\" (" + examples + ") - người học xem lại bài sẽ không"
                + " thấy giải thích");
    }

    /**
     * Câu gần giống ({@link TextSimilarity}) một câu đứng trước nó trong cùng đề, hoặc - với câu mới - một câu đã có
     * trong kho cùng cấp độ, chưa bị loại. Câu của chính đề này đã nhập trước không tính là câu trong kho: nó cũng có
     * trong đề nên đã được so ở bước trước. Chỉ cảnh báo, không chặn: có khi cố ý dùng lại câu để hỏi chữ khác.
     */
    private void warnSimilarQuestions(JlptLevel level, List<Built> all, List<Built> fresh, List<String> warnings) {
        List<Compared> inTest = new ArrayList<>();
        List<Compared> unchecked = new ArrayList<>();
        for (Built built : all) {
            ExamQuestion question = built.question();
            JlptQuestionType type = question.getQuestionType();
            String detail = JlptQuestionType.SENTENCE_ORDER.equals(type)
                    ? question.getExplanation()
                    : question.optionText(question.getCorrectOption());
            Compared compared = Compared.of(built.label(), comparedText(type, question.getSentence(), detail));
            if (compared != null) {
                inTest.add(compared);
                if (fresh.contains(built)) {
                    unchecked.add(compared);
                }
            }
        }
        Set<String> testRefs = all.stream().map(Built::ref).filter(Objects::nonNull).collect(Collectors.toSet());
        warnSimilar(inTest, unchecked, SIMILAR_QUESTION, "câu", () -> questionRepository
                .findTextsToCompare(level.name()).stream()
                .filter(row -> !testRefs.contains(row.getSourceRef()))
                .map(row -> Compared.of(storedName(row.getSourceRef(), row.getId()),
                        comparedText(row.getType(), row.getSentence(), row.getDetail())))
                .filter(Objects::nonNull)
                .toList(), warnings);
    }

    /** Đoạn văn / bài đọc gần giống một đoạn khác, như {@link #warnSimilarQuestions}; so với đoạn của mọi phần thi. */
    private void warnSimilarPassages(JlptLevel level, List<BuiltPassage> all, List<ExamPassage> newPassages,
                                     List<String> warnings) {
        List<Compared> inTest = new ArrayList<>();
        List<Compared> unchecked = new ArrayList<>();
        for (BuiltPassage built : all) {
            Compared compared = Compared.of(built.label(), built.passage().getContent());
            if (compared != null) {
                inTest.add(compared);
                if (newPassages.contains(built.passage())) {
                    unchecked.add(compared);
                }
            }
        }
        Set<String> testRefs = all.stream()
                .map(built -> built.passage().getSourceRef())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        warnSimilar(inTest, unchecked, SIMILAR_PASSAGE, "đoạn văn", () -> passageRepository
                .findTextsToCompare(level.name()).stream()
                .filter(row -> !testRefs.contains(row.getSourceRef()))
                .map(row -> Compared.of(storedName(row.getSourceRef(), row.getId()), row.getContent()))
                .filter(Objects::nonNull)
                .toList(), warnings);
    }

    /**
     * Báo mỗi nội dung của đề gần giống một nội dung đứng trước nó trong đề, và mỗi nội dung mới ({@code unchecked}) gần
     * giống một nội dung trong kho ({@code stored}, chỉ nạp khi có nội dung mới). Mỗi lần so chỉ báo nội dung giống nhất.
     */
    private static void warnSimilar(List<Compared> inTest, List<Compared> unchecked, double threshold, String kind,
                                    Supplier<List<Compared>> stored, List<String> warnings) {
        List<Compared> earlier = new ArrayList<>();
        for (Compared compared : inTest) {
            closest(compared, earlier, threshold).ifPresent(match -> warnings.add(compared.name() + ": gần giống "
                    + match.other().name() + " trong cùng đề (" + percent(match.score()) + ")"));
            earlier.add(compared);
        }
        if (unchecked.isEmpty()) {
            return;
        }
        List<Compared> bank = stored.get();
        for (Compared compared : unchecked) {
            closest(compared, bank, threshold).ifPresent(match -> warnings.add(compared.name() + ": gần giống " + kind
                    + " đã có trong kho (" + percent(match.score()) + ", " + match.other().name() + "): 「"
                    + quote(match.other().text()) + "」"));
        }
    }

    /** Nội dung giống {@code target} nhất trong {@code others}, nếu độ giống đạt {@code threshold}. */
    private static Optional<Match> closest(Compared target, List<Compared> others, double threshold) {
        Match best = null;
        for (Compared other : others) {
            double score = target.fingerprint().similarity(other.fingerprint());
            if (score >= threshold && (best == null || score > best.score())) {
                best = new Match(other, score);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Phần của câu đem so độ giống - luôn là câu đầy đủ, vì câu viết lại từ câu cũ thường được hỏi ở dạng khác; null =
     * không so. Câu điền chỗ trống so bằng câu đã điền lựa chọn đúng ({@code detail}); câu sắp xếp so bằng câu đúng
     * trong giải thích ({@code detail}) vì cột sentence chỉ là khung có ô trống; câu 用法 so bằng lựa chọn đúng vì 4 lựa
     * chọn là 4 câu. Câu của đoạn văn (文章の文法, Đọc hiểu) không so từng câu mà so cả đoạn văn.
     */
    static String comparedText(JlptQuestionType type, String sentence, String detail) {
        if (type == null) {
            return sentence;
        }
        return switch (type) {
            case CONTEXT, GRAMMAR_FORM -> sentence == null || detail == null
                    ? sentence
                    : sentence.replace(ExamQuestionGenerator.BLANK, detail);
            case SENTENCE_ORDER -> {
                Matcher answer = ORDER_ANSWER.matcher(detail == null ? "" : detail);
                yield answer.find() ? answer.group(1) : sentence;
            }
            case USAGE -> detail;
            case TEXT_GRAMMAR, READING_SHORT, READING_MID, READING_LONG, INFO_SEARCH -> null;
            default -> sentence;
        };
    }

    /** Tên của câu / đoạn văn trong kho trong cảnh báo: mã đề gốc (nếu có) và id, vd. "N3-21/NP/4 #10377". */
    private static String storedName(String sourceRef, Long id) {
        return (sourceRef == null ? "" : sourceRef + " ") + "#" + id;
    }

    /** Làm tròn xuống: 99,6% chưa phải như nhau hẳn nên không ghi 100%. */
    private static String percent(double score) {
        return (int) Math.floor(score * 100 + 1e-9) + "%";
    }

    private static String quote(String text) {
        String line = WHITESPACE.matcher(text).replaceAll(" ").strip();
        return line.codePointCount(0, line.length()) <= QUOTE_LENGTH ? line
                : line.substring(0, line.offsetByCodePoints(0, QUOTE_LENGTH)) + "…";
    }

    private static String stem(String written) {
        return TRAILING_HIRAGANA.matcher(written).replaceFirst("");
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * Một câu đã dựng từ file: mã câu đề gốc (null với bài đọc lẻ không có mã), nhãn để báo lỗi ("NP câu 15"), từ và mẫu
     * ngữ pháp để gắn với kho, đoạn văn chứa câu (null với câu đứng riêng).
     */
    private record Built(String ref, String label, ExamQuestion question, WordHint word, String grammarPattern,
                         ExamPassage passage) {
    }

    /** Từ câu hỏi kiểm tra: cách viết, và cách đọc nếu biết (để chọn đúng từ khi kho có nhiều từ cùng chữ). */
    private record WordHint(String written, String reading) {
    }

    /** Một đoạn văn đã dựng từ file, kèm nhãn để báo ("NP đoạn văn", "DH bài câu 28-30"). */
    private record BuiltPassage(String label, ExamPassage passage) {
    }

    /**
     * Một nội dung đem so độ giống: tên để báo (nhãn trong đề như "NP câu 4", hoặc mã và id trong kho) và chữ gốc để
     * trích.
     */
    private record Compared(String name, String text, TextSimilarity.Fingerprint fingerprint) {

        /** Null nếu không có gì để so. */
        static Compared of(String name, String text) {
            TextSimilarity.Fingerprint fingerprint = TextSimilarity.Fingerprint.of(text);
            return fingerprint.isEmpty() ? null : new Compared(name, text, fingerprint);
        }
    }

    private record Match(Compared other, double score) {
    }

    /**
     * Đề đọc từ file: các câu đã dựng (theo thứ tự trong đề), các đoạn văn và các lỗi tìm thấy. {@code code} null (bài
     * đọc lẻ không có mã) thì câu và đoạn văn không có mã đề gốc.
     */
    private static final class ParsedTest {
        private final String code;
        private final JlptLevel level;
        private final LocalDateTime now;
        private final List<Built> questions = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<BuiltPassage> passages = new ArrayList<>();

        ParsedTest(String code, JlptLevel level, LocalDateTime now) {
            this.code = code;
            this.level = level;
            this.now = now;
        }

        /** Các 問題 của một phần theo đúng thứ tự cấu trúc đề; số câu đánh liên tục từ 1. */
        void section(JlptBlueprintProperties.Section blueprint, String part, Map<JlptQuestionType, List<Item>> itemsByType,
                     ExamImportRequest.Passage passageItem) {
            Set<JlptQuestionType> planned = blueprint.getQuestions().keySet();
            itemsByType.forEach((type, items) -> {
                if (!items.isEmpty() && !planned.contains(type)) {
                    errors.add(part + ": đề " + level + " không có dạng " + type);
                }
            });
            int number = 1;
            int mondai = 0;
            for (Map.Entry<JlptQuestionType, Integer> entry : blueprint.getQuestions().entrySet()) {
                mondai++;
                JlptQuestionType type = entry.getKey();
                int count = entry.getValue();
                List<Item> items = JlptQuestionType.TEXT_GRAMMAR.equals(type)
                        ? (passageItem == null ? List.of() : orEmpty(passageItem.questions()))
                        : itemsByType.getOrDefault(type, List.of());
                if (items.size() != count) {
                    errors.add(part + " 問題" + mondai + " (" + type + "): cần " + count + " câu, file có " + items.size());
                }
                for (int i = 0; i < items.size(); i++) {
                    Item item = items.get(i);
                    if (item != null && !Objects.equals(item.number(), number + i)) {
                        errors.add(part + " 問題" + mondai + ": câu thứ " + (i + 1) + " phải là câu " + (number + i)
                                + ", file ghi " + item.number());
                    }
                }
                if (JlptQuestionType.TEXT_GRAMMAR.equals(type)) {
                    passage(part, passageItem, items, number);
                } else {
                    for (int i = 0; i < items.size(); i++) {
                        question(part, type, items.get(i), number + i, null, null);
                    }
                }
                number += count;
            }
        }

        /**
         * Đoạn văn: chỗ trống đánh theo số câu trong đề (【19】..【23】, câu điền hai chỗ 【20a】【20b】) được đánh lại
         * thành 【1】..【n】 như các đoạn văn khác trong kho.
         */
        private void passage(String part, ExamImportRequest.Passage item, List<Item> items, int start) {
            if (item == null) {
                return;
            }
            String label = part + " đoạn văn";
            String content = strip(item.content());
            if (content == null) {
                errors.add(label + ": thiếu \"noi_dung\" (toàn văn đoạn văn, chỗ trống đánh 【" + start + "】...)");
                return;
            }
            int end = start + items.size() - 1;
            Matcher matcher = ExamQuestionValidator.PASSAGE_BLANK.matcher(content);
            StringBuilder renumbered = new StringBuilder();
            while (matcher.find()) {
                int number = Integer.parseInt(matcher.group(1));
                if (number < start || number > end) {
                    errors.add(label + ": chỗ trống 【" + number + matcher.group(2) + "】 không ứng với câu nào (câu "
                            + start + "-" + end + ")");
                    // Đã báo ở trên; bỏ khỏi phần kiểm tra chỗ trống để không báo lần nữa. Có lỗi nên đoạn không được ghi.
                    matcher.appendReplacement(renumbered, "【?】");
                } else {
                    matcher.appendReplacement(renumbered,
                            Matcher.quoteReplacement("【" + (number - start + 1) + matcher.group(2) + "】"));
                }
            }
            matcher.appendTail(renumbered);
            List<Integer> blanks = IntStream.rangeClosed(1, items.size()).boxed().toList();
            ExamQuestionValidator.passageProblems(renumbered.toString(), blanks)
                    .forEach(problem -> errors.add(label + ": " + examNumbers(problem, start)));
            String title = strip(item.title());
            ExamPassage passage = ExamPassage.builder()
                    .jlptLevel(level)
                    .section(ExamSection.GRAMMAR)
                    .title(title == null ? "Đề " + code : title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH)))
                    .content(renumbered.toString())
                    .review(new ReviewState(ExamQuestionStatus.DRAFT))
                    .source(ExamQuestionSource.IMPORTED)
                    .sourceRef(code + "/" + part + "/" + start + "-" + end)
                    .createdAt(now)
                    .build();
            passages.add(new BuiltPassage(label, passage));
            for (int i = 0; i < items.size(); i++) {
                question(part, JlptQuestionType.TEXT_GRAMMAR, items.get(i), start + i, i + 1, passage);
            }
        }

        /**
         * Phần Đọc hiểu: các bài của từng 問題 theo đúng thứ tự cấu trúc đề, mỗi 問題 đủ số bài (số câu chia số câu mỗi
         * bài); số câu đánh liên tục từ {@code firstNumber}, như đề thật (Đọc hiểu N3 từ câu 24).
         */
        void reading(JlptBlueprintProperties.Section blueprint, Map<JlptQuestionType, List<ExamImportRequest.Passage>>
                passagesByType, int firstNumber, int firstMondai) {
            passagesByType.forEach((type, items) -> {
                if (!items.isEmpty() && !blueprint.getQuestions().containsKey(type)) {
                    errors.add(READING_PART + ": đề " + level + " không có dạng " + type);
                }
            });
            int number = firstNumber;
            int mondai = firstMondai - 1;
            for (Map.Entry<JlptQuestionType, Integer> entry : blueprint.getQuestions().entrySet()) {
                mondai++;
                JlptQuestionType type = entry.getKey();
                Integer perPassage = blueprint.questionsPerPassage(type).orElse(null);
                List<ExamImportRequest.Passage> items = passagesByType.getOrDefault(type, List.of());
                int planned = perPassage == null ? 1 : entry.getValue() / perPassage;
                if (items.size() != planned) {
                    errors.add(READING_PART + " 問題" + mondai + " (" + type + "): cần " + planned + " bài, file có "
                            + items.size());
                }
                for (ExamImportRequest.Passage item : items) {
                    number += readingPassage(type, perPassage, item, number);
                }
            }
        }

        /**
         * Một bài đọc dạng {@code type} có các câu hỏi đánh số từ {@code start}; câu hỏi lưu theo thứ tự trong bài
         * (1..n). Trả về số câu hỏi của bài để đánh số bài sau.
         */
        int readingPassage(JlptQuestionType type, Integer perPassage, ExamImportRequest.Passage item, int start) {
            if (item == null) {
                errors.add(READING_PART + " bài từ câu " + start + ": trống");
                return 0;
            }
            List<Item> items = orEmpty(item.questions());
            int end = start + Math.max(items.size(), 1) - 1;
            String range = start == end ? String.valueOf(start) : start + "-" + end;
            String label = READING_PART + " bài câu " + range;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != null && !Objects.equals(items.get(i).number(), start + i)) {
                    errors.add(label + ": câu thứ " + (i + 1) + " phải là câu " + (start + i) + ", file ghi "
                            + items.get(i).number());
                }
            }
            String content = strip(item.content());
            List<ExamQuestionValidator.PassageQuestion> numbered = IntStream.rangeClosed(1, items.size())
                    .mapToObj(i -> new ExamQuestionValidator.PassageQuestion(type, i))
                    .toList();
            ExamQuestionValidator.passageProblems(ExamSection.READING, content, numbered, perPassage)
                    .forEach(problem -> errors.add(label + ": " + problem));
            String title = strip(item.title());
            ExamPassage passage = ExamPassage.builder()
                    .jlptLevel(level)
                    .section(ExamSection.READING)
                    .title(title == null ? null : title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH)))
                    .content(content == null ? "" : content)
                    .review(new ReviewState(ExamQuestionStatus.DRAFT))
                    .source(ExamQuestionSource.IMPORTED)
                    .sourceRef(code == null ? null : code + "/" + READING_PART + "/" + range)
                    .createdAt(now)
                    .build();
            passages.add(new BuiltPassage(label, passage));
            for (int i = 0; i < items.size(); i++) {
                question(READING_PART, type, items.get(i), start + i, i + 1, passage);
            }
            return items.size();
        }

        /** Lỗi của đoạn văn ghi lại theo số câu trong đề: 【2】 của đoạn bắt đầu từ câu 19 là 【20】. */
        private static String examNumbers(String problem, int start) {
            Matcher matcher = ExamQuestionValidator.PASSAGE_BLANK.matcher(problem);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(
                        "【" + (Integer.parseInt(matcher.group(1)) + start - 1) + matcher.group(2) + "】"));
            }
            matcher.appendTail(result);
            return result.toString();
        }

        /**
         * Dựng một câu theo dạng 問題 của nó, thuộc đoạn văn {@code passage} (null với câu đứng riêng); câu sai cấu trúc
         * thì ghi lỗi và bỏ qua.
         */
        private void question(String part, JlptQuestionType type, Item item, int number, Integer blankNo,
                              ExamPassage passage) {
            String label = part + " câu " + number;
            if (item == null) {
                errors.add(label + ": trống");
                return;
            }
            List<String> problems = new ArrayList<>();
            List<String> options = options(item.options());
            Integer answer = item.answer();
            String sentence = strip(item.sentence());
            String highlight = strip(item.highlight());
            String explanation = strip(item.explanation());
            String text;
            QuizDirection skill = null;
            WordHint word = null;
            switch (type) {
                case KANJI_READING -> {
                    text = KANJI_READING_TEXT;
                    skill = QuizDirection.KANJI_TO_READING;
                    word = hint(item.word(), highlight, option(options, answer));
                }
                case ORTHOGRAPHY -> {
                    text = ORTHOGRAPHY_TEXT;
                    skill = QuizDirection.READING_TO_KANJI;
                    word = hint(item.word(), option(options, answer), highlight);
                }
                case CONTEXT -> {
                    text = CONTEXT_TEXT;
                    skill = QuizDirection.MEANING;
                    sentence = blank(sentence);
                    word = hint(item.word(), option(options, answer), null);
                }
                case PARAPHRASE -> {
                    text = QuestionDraftService.PARAPHRASE_PART_TEXT;
                    word = hint(item.word(), highlight, null);
                }
                case USAGE -> {
                    String keyword = strip(item.keyword());
                    if (keyword == null) {
                        problems.add("thiếu \"tu_khoa\" (từ được hỏi cách dùng)");
                    }
                    text = "Chọn câu dùng từ 「" + keyword + "」 đúng nhất.";
                    sentence = null;
                    highlight = null;
                    word = keyword == null ? null : hint(item.word(), keyword, null);
                }
                case GRAMMAR_FORM -> {
                    text = QuestionDraftService.GRAMMAR_FORM_TEXT;
                    sentence = blank(sentence);
                }
                case SENTENCE_ORDER -> {
                    text = QuestionDraftService.SENTENCE_ORDER_TEXT;
                    sentence = null;
                    Order order = order(item, options, problems);
                    if (order != null) {
                        answer = order.answer();
                        sentence = order.sentence();
                        // Câu đúng dựng từ thứ tự đứng đầu, giải thích soạn tay (dịch, mẫu ngữ pháp) xuống dòng bên dưới
                        explanation = order.explanation() + (explanation == null ? "" : "\n" + explanation);
                    }
                }
                case TEXT_GRAMMAR -> {
                    text = "【" + blankNo + "】";
                    sentence = null;
                    highlight = null;
                }
                case READING_SHORT, READING_MID, READING_LONG, INFO_SEARCH -> {
                    // "cau_hoi" của bài đọc là câu hỏi về bài, vd. 「この文章を書いた人が一番言いたいことは何か。」
                    text = sentence;
                    sentence = null;
                    highlight = null;
                }
                default -> throw new IllegalStateException("Dạng câu chưa hỗ trợ nhập: " + type);
            }
            boolean validAnswer = answer != null && answer >= 1 && answer <= 4;
            if (!validAnswer) {
                problems.add("\"dung\" phải là số của lựa chọn đúng, 1-4");
            }
            if (highlight != null && highlight.length() > ExamQuestionValidator.MAX_HIGHLIGHT_LENGTH) {
                problems.add("phần gạch chân dài quá " + ExamQuestionValidator.MAX_HIGHLIGHT_LENGTH + " ký tự");
            }
            boolean orderNotBuilt = JlptQuestionType.SENTENCE_ORDER.equals(type) && sentence == null;
            if (!orderNotBuilt) {
                problems.addAll(ExamQuestionValidator.problems(type, text, sentence, highlight, options,
                        validAnswer ? letter(answer) : "A"));
            }
            if (!problems.isEmpty()) {
                problems.forEach(problem -> errors.add(label + ": " + problem));
                return;
            }
            ExamQuestion question = ExamQuestion.builder()
                    .jlptLevel(level)
                    .questionText(text)
                    .sentence(sentence)
                    .highlight(highlight)
                    .optionA(options.get(0))
                    .optionB(options.get(1))
                    .optionC(options.get(2))
                    .optionD(options.get(3))
                    .correctOption(letter(answer))
                    .explanation(explanation)
                    .skill(skill)
                    .questionType(type)
                    .blankNo(blankNo)
                    .review(new ReviewState(ExamQuestionStatus.DRAFT))
                    .source(ExamQuestionSource.IMPORTED)
                    .sourceRef(code == null ? null : code + "/" + part + "/" + number)
                    .build();
            questions.add(new Built(question.getSourceRef(), label, question, word, strip(item.grammarPattern()),
                    passage));
        }

        /** Câu sắp xếp dựng từ phần trước/sau, thứ tự đúng của 4 vế và ô ★; đáp án suy ra phải khớp "dung" nếu có ghi. */
        private record Order(int answer, String sentence, String explanation) {
        }

        private static Order order(Item item, List<String> options, List<String> problems) {
            List<Integer> order = item.order();
            Integer star = item.star();
            if (order == null || order.size() != 4 || !new HashSet<>(order).equals(Set.of(1, 2, 3, 4))) {
                problems.add("\"thu_tu_dung\" phải là thứ tự đúng của 4 lựa chọn, vd. [2, 4, 3, 1]");
                return null;
            }
            if (star == null || star < 1 || star > 4) {
                problems.add("\"vi_tri_sao\" phải là ô có dấu ★, 1-4");
                return null;
            }
            if (options.size() != 4) {
                return null;
            }
            int atStar = order.get(star - 1);
            if (item.answer() != null && item.answer() != atStar) {
                problems.add("theo thứ tự đúng, ô ★ (ô " + star + ") là lựa chọn " + atStar + " 「" + options.get(atStar - 1)
                        + "」 nhưng \"dung\" ghi " + item.answer());
            }
            String before = Objects.requireNonNullElse(strip(item.before()), "");
            String after = Objects.requireNonNullElse(strip(item.after()), "");
            String slots = IntStream.rangeClosed(1, 4)
                    .mapToObj(slot -> slot == star ? ExamQuestionValidator.ORDER_STAR : ExamQuestionValidator.ORDER_SLOT)
                    .collect(Collectors.joining(" "));
            List<String> parts = order.stream().map(position -> options.get(position - 1)).toList();
            String explanation = "Câu đúng: " + before + String.join("", parts) + after + " ("
                    + String.join(" → ", parts) + "). Vế ở vị trí ★ là 「" + options.get(atStar - 1) + "」.";
            return new Order(atStar, (before + " " + slots + " " + after).strip(), explanation);
        }

        private static WordHint hint(String word, String written, String reading) {
            String explicit = strip(word);
            if (explicit != null) {
                return new WordHint(explicit, null);
            }
            return written == null ? null : new WordHint(written, reading);
        }

        /** Lựa chọn đúng (để gắn với kho từ), null nếu "dung" không hợp lệ. */
        private static String option(List<String> options, Integer answer) {
            return answer != null && answer >= 1 && answer <= options.size() ? options.get(answer - 1) : null;
        }

        /** Bỏ khoảng trắng thừa, và số thứ tự chép kèm ("1 こわして") nếu cả 4 lựa chọn đều có đúng số của mình. */
        private static List<String> options(List<String> raw) {
            if (raw == null) {
                return List.of();
            }
            List<String> options = raw.stream().map(option -> option == null ? "" : option.strip()).toList();
            boolean numbered = options.size() == 4 && IntStream.range(0, 4).allMatch(i -> {
                Matcher matcher = OPTION_NUMBER.matcher(options.get(i));
                return matcher.find() && toAsciiDigit(matcher.group(1)) == i + 1;
            });
            return numbered
                    ? options.stream().map(option -> OPTION_NUMBER.matcher(option).replaceFirst("").strip()).toList()
                    : options;
        }

        private static int toAsciiDigit(String digit) {
            char c = digit.charAt(0);
            return c >= '１' && c <= '４' ? c - '１' + 1 : c - '0';
        }

        /** Ô trống viết lệch (（ ）, ( ), （  ）...) đổi về ô chuẩn （　　）. */
        private static String blank(String sentence) {
            return sentence == null ? null
                    : QuestionDraftService.LOOSE_BLANK.matcher(sentence).replaceAll(ExamQuestionGenerator.BLANK);
        }

        private static String letter(int answer) {
            return String.valueOf((char) ('A' + answer - 1));
        }

        private static String strip(String value) {
            return StringUtils.hasText(value) ? value.strip() : null;
        }
    }
}
