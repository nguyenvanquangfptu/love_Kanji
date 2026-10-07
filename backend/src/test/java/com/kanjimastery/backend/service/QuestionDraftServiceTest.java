package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionDraftServiceTest {

    private static final GrammarPoint AFTER = GrammarPoint.builder().id(3L).jlptLevel(JlptLevel.N5).lesson("N5-16")
            .pattern("Vてから").meaningVi("Sau khi làm V1 rồi mới V2").connection("V1て + から、V2").build();
    private static final Kanji LEAVE = Kanji.builder().id(11L).character("預ける").reading("あずける")
            .meaning("Gửi, nhờ giữ hộ").build();
    private static final Kanji REFUSE = Kanji.builder().id(12L).character("断る").reading("ことわる")
            .meaning("Từ chối").build();
    private static final Kanji DELIVER = Kanji.builder().id(13L).character("届ける").reading("とどける")
            .meaning("Gửi đến, mang đến").build();
    private static final Kanji BUT = Kanji.builder().id(14L).character("でも").reading("でも").meaning("Nhưng").build();
    private static final Kanji PASS = Kanji.builder().id(21L).character("経つ").reading("たつ")
            .meaning("(Thời gian) trôi qua").build();
    private static final Kanji ENDURE = Kanji.builder().id(22L).character("我慢する").reading("がまんする")
            .meaning("Chịu đựng").build();

    @Mock
    private GeminiClient geminiClient;
    @Mock
    private GrammarPointRepository grammarPointRepository;
    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private VocabularyLevelChecker levelChecker;

    private QuestionDraftService service;

    @BeforeEach
    void setUp() {
        DraftReviewer reviewer = new DraftReviewer(geminiClient, new ObjectMapper(), levelChecker);
        service = new QuestionDraftService(geminiClient, reviewer, grammarPointRepository, questionRepository,
                kanjiRepository, blueprints());
    }

    @Test
    void draft_shouldQueueWellFormedDrafts_rejectBrokenOnes_andFlagWhatTheSecondSolveDisagreesWith() {
        when(geminiClient.isEnabled()).thenReturn(true);
        when(grammarPointRepository.findById(3L)).thenReturn(Optional.of(AFTER));
        when(questionRepository.findSentencesByGrammarPoint(3L)).thenReturn(List.of());
        // Kho từ nhỏ: 運転 là từ N3, dùng trong câu N5 thì vượt cấp.
        when(levelChecker.open(JlptLevel.N5)).thenReturn(new VocabularyLevelChecker.Session(5, Map.of(
                "ご飯", 5, "食べる", 5, "歯", 5, "磨く", 5, "手", 5, "洗う", 5, "運転", 3, "練習", 5, "テレビ", 5,
                "見る", 5)));
        String drafts = """
                ```json
                [
                  {"sentence": "ご飯を食べ（ ）、歯を磨きます。", "options": ["てから", "ながら", "たり", "ても"],
                   "answer": 0, "explanation": "Vてから: làm xong V1 rồi mới V2."},
                  {"sentence": "宿題をし（　　）、遊びます。", "options": ["てから", "てから", "たら", "ても"],
                   "answer": 0, "explanation": "Trùng lựa chọn."},
                  {"sentence": "thiếu lựa chọn", "options": ["てから"], "answer": 0},
                  {"sentence": "手を洗っ（　　）、ご飯を食べます。", "options": ["てから", "ながら", "ても", "たり"],
                   "answer": 0, "explanation": "..."},
                  {"sentence": "運転を練習し（　　）、テレビを見ます。", "options": ["てから", "たら", "ても", "ながら"],
                   "answer": 0, "explanation": "..."}
                ]
                ```""";
        when(geminiClient.generateJson(anyString())).thenReturn(Optional.of(drafts)).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            // Câu 1 giải đúng; câu 2 chọn ながら; câu 3 giải đúng nhưng thấy たら cũng được.
            return Optional.of("""
                    [{"index": 1, "answer": %d, "alsoCorrect": [], "note": ""},
                     {"index": 2, "answer": %d, "alsoCorrect": [], "note": "Hai việc làm cùng lúc"},
                     {"index": 3, "answer": %d, "alsoCorrect": [%d], "note": "たら cũng tự nhiên"}]
                    """.formatted(option(prompt, 1, "てから"), option(prompt, 2, "ながら"), option(prompt, 3, "てから"),
                    option(prompt, 3, "たら")));
        });

        QuestionDraftService.DraftResult result = service.draft(3L, JlptQuestionType.GRAMMAR_FORM, 5);

        assertThat(result).isEqualTo(new QuestionDraftService.DraftResult(3, 2, 1, 1));
        List<ExamQuestion> saved = saved();
        assertThat(saved).hasSize(4).allSatisfy(question -> {
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.AI);
            assertThat(question.getJlptLevel()).isEqualTo(JlptLevel.N5);
            assertThat(question.getQuestionType()).isEqualTo(JlptQuestionType.GRAMMAR_FORM);
            assertThat(question.getGrammarPointIds()).containsExactly(3L);
        });
        ExamQuestion clean = saved.get(0);
        // Ô trống AI viết lệch （ ） được sửa lại thành （　　）; đáp án đúng vẫn là てから sau khi xáo lựa chọn.
        assertThat(clean.getSentence()).isEqualTo("ご飯を食べ（　　）、歯を磨きます。");
        assertThat(correct(clean)).isEqualTo("てから");
        assertThat(clean.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(clean.getFlag()).isNull();
        assertThat(saved.get(1).getStatus()).isEqualTo(ExamQuestionStatus.REJECTED);
        assertThat(saved.get(1).getReviewNote()).contains("có lựa chọn trùng nhau");
        assertThat(saved.get(2).getFlag()).isEqualTo(ExamQuestionFlag.WRONG_ANSWER);
        assertThat(saved.get(2).getReviewNote()).contains("Hai việc làm cùng lúc");
        // Vừa có từ vượt cấp vừa bị nghi hai đáp án: giữ cờ nặng hơn, ghi chú đủ cả hai.
        assertThat(saved.get(3).getFlag()).isEqualTo(ExamQuestionFlag.AMBIGUOUS);
        assertThat(saved.get(3).getReviewNote()).contains("運転").contains("たら cũng tự nhiên");
    }

    @Test
    void draft_shouldBuildSentenceOrderQuestions_fromThePartsInTheirRightOrder() {
        when(geminiClient.isEnabled()).thenReturn(true);
        when(grammarPointRepository.findById(3L)).thenReturn(Optional.of(AFTER));
        when(questionRepository.findSentencesByGrammarPoint(3L)).thenReturn(List.of("既にある文。"));
        when(levelChecker.open(JlptLevel.N5)).thenReturn(new VocabularyLevelChecker.Session(5, Map.of(
                "ご飯", 5, "食べる", 5, "歯", 5, "磨く", 5, "毎晩", 5)));
        when(geminiClient.generateJson(anyString())).thenReturn(Optional.of("""
                [{"before": "毎晩", "parts": ["ご飯を", "食べて", "から", "歯を"], "after": "磨きます。", "star": 3,
                  "explanation": "Vてから."}]
                """), Optional.empty());

        QuestionDraftService.DraftResult result = service.draft(3L, JlptQuestionType.SENTENCE_ORDER, 1);

        assertThat(result).isEqualTo(new QuestionDraftService.DraftResult(1, 0, 0, 0));
        ExamQuestion question = saved().get(0);
        assertThat(question.getSentence()).isEqualTo("毎晩 ＿＿＿ ＿＿＿ ＿★＿ ＿＿＿ 磨きます。");
        assertThat(correct(question)).isEqualTo("から");
        assertThat(List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD()))
                .containsExactlyInAnyOrder("ご飯を", "食べて", "から", "歯を");
        assertThat(question.getExplanation()).contains("毎晩ご飯を食べてから歯を磨きます。");
        // Gemini không giải lại được: không gắn cờ nhưng nhắc người duyệt.
        assertThat(question.getReviewNote()).contains("Chưa nhờ AI giải lại được");
    }

    @Test
    void draft_shouldRefuse_whenGeminiIsNotConfigured_orTheTypeCannotBeDrafted() {
        when(geminiClient.isEnabled()).thenReturn(false, true);

        assertThatThrownBy(() -> service.draft(3L, JlptQuestionType.GRAMMAR_FORM, 5))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("GEMINI_API_KEY");
        assertThatThrownBy(() -> service.draft(3L, JlptQuestionType.TEXT_GRAMMAR, 5))
                .isInstanceOf(BadRequestException.class);
        verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    void draftVocabulary_shouldAskForLessonWordsWithoutAQuestion_andRejectUsageSentencesMissingTheWord() {
        when(geminiClient.isEnabled()).thenReturn(true);
        // 届ける đã có câu 用法; でも là liên từ - không hỏi.
        when(questionRepository.findWordIdsWithQuestion("N4", JlptQuestionType.USAGE.name())).thenReturn(List.of(13L));
        when(kanjiRepository.findAllByTagNamePrefix("N4-%")).thenReturn(List.of(LEAVE, REFUSE, DELIVER, BUT));
        when(levelChecker.open(JlptLevel.N4)).thenReturn(new VocabularyLevelChecker.Session(4, Map.of()));
        List<String> prompts = new ArrayList<>();
        when(geminiClient.generateJson(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            prompts.add(prompt);
            return Optional.of(prompts.size() == 1 ? """
                    [{"index": %d, "options": ["旅行の間、ねこを友だちに預けた。", "窓をしっかり預けた。",
                       "先生に質問を預けた。", "会議の時間を預けた。"], "answer": 0, "explanation": "預ける: gửi."},
                     {"index": %d, "options": ["誘いを断った。", "古い服を捨てた。", "道を断った。", "試合を断った。"],
                      "answer": 0, "explanation": "..."}]
                    """.formatted(wordNumber(prompt, "預ける"), wordNumber(prompt, "断る")) : """
                    [{"index": 1, "answer": %d, "alsoCorrect": [%d], "note": "câu 2 cũng tạm được"}]
                    """.formatted(option(prompt, 1, "旅行の間、ねこを友だちに預けた。"), option(prompt, 1, "窓をしっかり預けた。")));
        });

        QuestionDraftService.DraftResult result = service.draftVocabulary("n4", JlptQuestionType.USAGE, 8);

        assertThat(result).isEqualTo(new QuestionDraftService.DraftResult(1, 1, 1, 0));
        assertThat(prompts.get(0)).contains("預ける (あずける) - Gửi, nhờ giữ hộ").contains("断る")
                .doesNotContain("届ける").doesNotContain(". でも - ");
        assertThat(prompts.get(1)).contains("Từ 「預ける」");
        List<ExamQuestion> saved = saved();
        assertThat(saved).hasSize(2).allSatisfy(question -> {
            assertThat(question.getQuestionType()).isEqualTo(JlptQuestionType.USAGE);
            assertThat(question.getJlptLevel()).isEqualTo(JlptLevel.N4);
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.AI);
            assertThat(question.getSentence()).isNull();
        });
        ExamQuestion leave = saved.get(0);
        assertThat(leave.getQuestionText()).isEqualTo("Chọn câu dùng từ 「預ける」 đúng nhất.");
        assertThat(leave.getKanjiIds()).containsExactly(11L);
        assertThat(correct(leave)).isEqualTo("旅行の間、ねこを友だちに預けた。");
        assertThat(leave.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(leave.getFlag()).isEqualTo(ExamQuestionFlag.AMBIGUOUS);
        ExamQuestion refuse = saved.get(1);
        assertThat(refuse.getKanjiIds()).containsExactly(12L);
        assertThat(refuse.getStatus()).isEqualTo(ExamQuestionStatus.REJECTED);
        assertThat(refuse.getReviewNote()).contains("có câu không dùng từ 「断る」");
    }

    @Test
    void draftVocabulary_shouldUnderlineThePartWithTheWord_fromN3_andTheWholeSentence_inN4AndN5() {
        when(geminiClient.isEnabled()).thenReturn(true);
        when(questionRepository.findWordIdsWithQuestion(anyString(), anyString())).thenReturn(List.of());
        when(kanjiRepository.findAllByTagNamePrefix("N3-%")).thenReturn(List.of(PASS, ENDURE));
        when(kanjiRepository.findAllByTagNamePrefix("N4-%")).thenReturn(List.of(PASS));
        when(levelChecker.open(any())).thenAnswer(invocation ->
                new VocabularyLevelChecker.Session(VocabularyLevelChecker.rank(invocation.getArgument(0)), Map.of()));
        List<String> prompts = new ArrayList<>();
        when(geminiClient.generateJson(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            prompts.add(prompt);
            return Optional.of(switch (prompts.size()) {
                // N3: một câu đúng; một câu gạch chân chỗ không có từ; một mục hỏi từ không có trong danh sách.
                case 1 -> """
                        [{"index": %d, "sentence": "日本に来てから、もう三年が経った。", "highlight": "経った",
                          "options": ["過ぎた", "残った", "始まった", "止まった"], "answer": 0, "explanation": "..."},
                         {"index": %d, "sentence": "歯が痛かったが、病院が開くまで我慢した。", "highlight": "病院が開くまで",
                          "options": ["耐えた", "迷った", "休んだ", "急いだ"], "answer": 0},
                         {"index": 9, "sentence": "...", "highlight": "...", "options": ["1", "2", "3", "4"], "answer": 0}]
                        """.formatted(wordNumber(prompt, "経つ"), wordNumber(prompt, "我慢する"));
                case 2 -> """
                        [{"index": 1, "answer": %d, "alsoCorrect": [], "note": ""}]
                        """.formatted(option(prompt, 1, "過ぎた"));
                // N4: cả câu được gạch chân, lựa chọn là câu.
                case 3 -> """
                        [{"index": 1, "sentence": "日本に来て三年が経ちました。",
                          "options": ["日本に来て三年になりました。", "日本に三年いませんでした。", "三年前に国へ帰りました。",
                                      "三年後に日本へ来ます。"], "answer": 0, "explanation": "..."}]
                        """;
                default -> """
                        [{"index": 1, "answer": %d, "alsoCorrect": [], "note": ""}]
                        """.formatted(option(prompt, 1, "日本に来て三年になりました。"));
            });
        });

        QuestionDraftService.DraftResult n3 = service.draftVocabulary("N3", JlptQuestionType.PARAPHRASE, 2);
        QuestionDraftService.DraftResult n4 = service.draftVocabulary("N4", JlptQuestionType.PARAPHRASE, 1);

        assertThat(n3).isEqualTo(new QuestionDraftService.DraftResult(1, 0, 1, 1));
        assertThat(n4).isEqualTo(new QuestionDraftService.DraftResult(1, 0, 0, 0));
        assertThat(prompts.get(1)).contains("日本に来てから、もう三年が経った。 (phần gạch chân: 「経った」)");
        assertThat(prompts.get(2)).contains("kiểu N4, N5");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionRepository, times(2)).saveAll(captor.capture());
        List<ExamQuestion> n3Saved = captor.getAllValues().get(0);
        ExamQuestion pass = n3Saved.get(0);
        assertThat(pass.getQuestionText()).isEqualTo(QuestionDraftService.PARAPHRASE_PART_TEXT);
        assertThat(pass.getHighlight()).isEqualTo("経った");
        assertThat(correct(pass)).isEqualTo("過ぎた");
        assertThat(pass.getKanjiIds()).containsExactly(21L);
        assertThat(pass.getFlag()).isNull();
        assertThat(n3Saved.get(1).getStatus()).isEqualTo(ExamQuestionStatus.REJECTED);
        assertThat(n3Saved.get(1).getReviewNote()).contains("phần gạch chân không có từ 「我慢する」");
        ExamQuestion whole = captor.getAllValues().get(1).get(0);
        assertThat(whole.getQuestionText()).isEqualTo(QuestionDraftService.PARAPHRASE_SENTENCE_TEXT);
        assertThat(whole.getHighlight()).isEqualTo(whole.getSentence()).isEqualTo("日本に来て三年が経ちました。");
        assertThat(correct(whole)).isEqualTo("日本に来て三年になりました。");
    }

    @Test
    void draftVocabulary_shouldRefuse_typesTheLevelsExamDoesNotHave_andWhenEveryWordHasAQuestion() {
        when(geminiClient.isEnabled()).thenReturn(true);
        when(questionRepository.findWordIdsWithQuestion("N4", JlptQuestionType.USAGE.name())).thenReturn(List.of(11L));
        when(kanjiRepository.findAllByTagNamePrefix("N4-%")).thenReturn(List.of(LEAVE));

        // Đề N5 không có 用法.
        assertThatThrownBy(() -> service.draftVocabulary("N5", JlptQuestionType.USAGE, 5))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("không có dạng câu này");
        assertThatThrownBy(() -> service.draftVocabulary("N4", JlptQuestionType.CONTEXT, 5))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.draftVocabulary("N4", JlptQuestionType.USAGE, 5))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("đều đã có câu");
        verify(geminiClient, never()).generateJson(anyString());
    }

    @Test
    void mentions_shouldFindTheWordInAnyForm_butNotByAOneKanaReading() {
        assertThat(QuestionDraftService.stem("預ける")).isEqualTo("預け");
        assertThat(QuestionDraftService.stem("帰宅する")).isEqualTo("帰宅");
        assertThat(QuestionDraftService.stem("迷惑")).isEqualTo("迷惑");
        assertThat(QuestionDraftService.mentions("ねこを友だちに預けた。", LEAVE)).isTrue();
        assertThat(QuestionDraftService.mentions("ねこを友だちにあずけた。", LEAVE)).isTrue();
        assertThat(QuestionDraftService.mentions("毎日がまんしています。", ENDURE)).isTrue();
        assertThat(QuestionDraftService.mentions("窓を閉めた。", LEAVE)).isFalse();
        Kanji see = Kanji.builder().id(1L).character("見る").reading("み(る)").meaning("Nhìn").build();
        assertThat(QuestionDraftService.mentions("テレビを見た。", see)).isTrue();
        assertThat(QuestionDraftService.mentions("みんなで行った。", see)).isFalse();
    }

    /** Cấu trúc phần Từ vựng như jlpt-blueprints.yml: N5 không có 用法. */
    static JlptBlueprintProperties blueprints() {
        JlptBlueprintProperties blueprints = new JlptBlueprintProperties();
        for (JlptLevel level : List.of(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3)) {
            JlptBlueprintProperties.Section vocabulary = new JlptBlueprintProperties.Section();
            vocabulary.setName(ExamSection.VOCABULARY);
            Map<JlptQuestionType, Integer> questions = new LinkedHashMap<>(Map.of(JlptQuestionType.KANJI_READING, 7,
                    JlptQuestionType.CONTEXT, 6, JlptQuestionType.PARAPHRASE, 3));
            if (level != JlptLevel.N5) {
                questions.put(JlptQuestionType.USAGE, 4);
            }
            vocabulary.setQuestions(questions);
            JlptBlueprintProperties.Section grammar = new JlptBlueprintProperties.Section();
            grammar.setName(ExamSection.GRAMMAR);
            grammar.setQuestions(new LinkedHashMap<>(Map.of(JlptQuestionType.GRAMMAR_FORM, 13,
                    JlptQuestionType.TEXT_GRAMMAR, 4)));
            JlptBlueprintProperties.Level blueprint = new JlptBlueprintProperties.Level();
            blueprint.setSections(List.of(vocabulary, grammar));
            blueprints.getLevels().put(level, blueprint);
        }
        return blueprints;
    }

    /** Số thứ tự của từ trong danh sách từ của prompt viết nháp ("2. 預ける (あずける) - ..."). */
    private static int wordNumber(String prompt, String word) {
        Matcher matcher = Pattern.compile("(?m)^(\\d+)\\. " + Pattern.quote(word) + " ").matcher(prompt);
        if (!matcher.find()) {
            throw new IllegalStateException("Không thấy từ " + word + " trong prompt");
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** Số thứ tự (1-4) của lựa chọn {@code option} trong câu thứ {@code number} của prompt giải lại. */
    static int option(String prompt, int number, String option) {
        List<String> block = prompt.substring(prompt.indexOf("Câu " + number + ":")).lines().toList();
        for (int index = 1; index <= 4; index++) {
            String prefix = "  " + index + ") ";
            String value = block.stream().filter(line -> line.startsWith(prefix)).findFirst().orElseThrow()
                    .substring(prefix.length());
            if (value.equals(option)) {
                return index;
            }
        }
        throw new IllegalStateException("Không thấy " + option + " ở câu " + number);
    }

    private List<ExamQuestion> saved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    static String correct(ExamQuestion question) {
        return switch (question.getCorrectOption()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            default -> question.getOptionD();
        };
    }
}
