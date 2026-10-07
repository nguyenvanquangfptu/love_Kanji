package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassageDraftServiceTest {

    private static final GrammarPoint AFTER = GrammarPoint.builder().id(3L).jlptLevel(JlptLevel.N4).pattern("〜てから")
            .meaningVi("Sau khi").build();
    /** Đoạn văn 4 chỗ trống, AI viết lệch ký hiệu chỗ trống 【２】 và [3]. */
    private static final String PASSAGE = """
            {"title": "わたしの休みの日",
             "content": "休みの日は、朝ご飯を食べ【1】、公園を散歩します。【２】、雨の日は家にいます。家で本を[3]。\
            友だちと話すのも【4】です。",
             "questions": [
               {"blank": 1, "options": ["てから", "ながら", "たり", "ても"], "answer": 0, "grammar": "~てから",
                "explanation": "Làm xong rồi mới đi dạo."},
               {"blank": 2, "options": ["でも", "それで", "それに", "だから"], "answer": 0, "grammar": ""},
               {"blank": 3, "options": ["読みます", "読みました", "読みません", "読みましょう"], "answer": 0, "grammar": ""},
               {"blank": 4, "options": ["好き", "好きな", "好きだ", "好きに"], "answer": 0, "grammar": ""}
             ]}
            """;

    @Mock
    private GeminiClient geminiClient;
    @Mock
    private VocabularyLevelChecker levelChecker;
    @Mock
    private GrammarPointRepository grammarPointRepository;
    @Mock
    private ExamPassageRepository passageRepository;
    @Mock
    private ExamQuestionRepository questionRepository;

    private PassageDraftService service;

    @BeforeEach
    void setUp() {
        DraftReviewer reviewer = new DraftReviewer(geminiClient, new ObjectMapper(), levelChecker);
        service = new PassageDraftService(geminiClient, reviewer, levelChecker, grammarPointRepository,
                passageRepository, questionRepository, QuestionDraftServiceTest.blueprints(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.systemDefaultZone());
    }

    @Test
    void draft_shouldQueueThePassageWithItsBlanks_linkGrammarPoints_andFlagWhatTheSecondSolveDisagreesWith() {
        givenTheLevelHasGrammarAndAPassage();
        // 散歩 là từ N3 trong kho nhỏ này: chỉ một từ vượt cấp thì ghi chú, không gắn cờ.
        when(levelChecker.open(JlptLevel.N4)).thenReturn(new VocabularyLevelChecker.Session(4, Map.of(
                "休み", 5, "食べる", 5, "公園", 5, "散歩", 3, "雨", 5, "家", 5, "本", 5, "読む", 5, "話す", 5,
                "好き", 5)));
        List<String> prompts = new ArrayList<>();
        when(geminiClient.generateJson(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            prompts.add(prompt);
            // Giải lại: chỗ trống 2 chọn それで (khác đáp án), các chỗ khác đúng.
            return Optional.of(prompts.size() == 1 ? PASSAGE : """
                    [{"index": 1, "answer": %d, "alsoCorrect": []},
                     {"index": 2, "answer": %d, "alsoCorrect": [], "note": "Nối nguyên nhân"},
                     {"index": 3, "answer": %d, "alsoCorrect": []},
                     {"index": 4, "answer": %d, "alsoCorrect": []}]
                    """.formatted(QuestionDraftServiceTest.option(prompt, 1, "てから"),
                    QuestionDraftServiceTest.option(prompt, 2, "それで"),
                    QuestionDraftServiceTest.option(prompt, 3, "読みます"),
                    QuestionDraftServiceTest.option(prompt, 4, "好き")));
        });
        when(passageRepository.save(any(ExamPassage.class))).thenAnswer(invocation -> {
            ExamPassage passage = invocation.getArgument(0);
            passage.setId(7L);
            return passage;
        });

        PassageDraftService.PassageDraftResult result = service.draft("n4");

        assertThat(result).isEqualTo(new PassageDraftService.PassageDraftResult(7L, ExamQuestionStatus.DRAFT,
                ExamQuestionFlag.WRONG_ANSWER, 4));
        assertThat(prompts.get(0)).contains("JLPT N4").contains("4 chỗ trống").contains("〜てから")
                .contains("hãy viết về chủ đề khác: 駅までの道");
        assertThat(prompts.get(1)).contains("休みの日は、朝ご飯を食べ【1】").contains("Câu 2: Chỗ trống 【2】");
        ExamPassage passage = savedPassage();
        assertThat(passage.getContent()).isEqualTo("休みの日は、朝ご飯を食べ【1】、公園を散歩します。【2】、雨の日は家にいます。"
                + "家で本を【3】。友だちと話すのも【4】です。");
        assertThat(passage.getTitle()).isEqualTo("わたしの休みの日");
        assertThat(passage.getSource()).isEqualTo(ExamQuestionSource.AI);
        assertThat(passage.getReviewNote()).contains("Từ vượt cấp độ: 散歩");

        List<ExamQuestion> questions = savedQuestions();
        assertThat(questions).extracting(ExamQuestion::getBlankNo).containsExactly(1, 2, 3, 4);
        assertThat(questions).allSatisfy(question -> {
            assertThat(question.getPassageId()).isEqualTo(7L);
            assertThat(question.getQuestionType()).isEqualTo(JlptQuestionType.TEXT_GRAMMAR);
            assertThat(question.getQuestionText()).isEqualTo("【" + question.getBlankNo() + "】");
            assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.AI);
        });
        // "~てから" khớp mẫu 〜てから của cấp độ.
        assertThat(questions.get(0).getGrammarPointIds()).containsExactly(3L);
        assertThat(questions.get(1).getGrammarPointIds()).isEmpty();
        assertThat(QuestionDraftServiceTest.correct(questions.get(0))).isEqualTo("てから");
        assertThat(questions.get(1).getFlag()).isEqualTo(ExamQuestionFlag.WRONG_ANSWER);
        assertThat(questions.get(1).getReviewNote()).contains("Nối nguyên nhân");
    }

    @Test
    void draft_shouldRejectThePassageWhole_whenItsBlanksDoNotMatchTheExam() {
        givenTheLevelHasGrammarAndAPassage();
        // Đề N4 cần 4 chỗ trống; AI chỉ viết 3, và chỗ trống 【4】 vẫn nằm trong đoạn.
        String threeQuestions = PASSAGE.replace("""
                ,
                   {"blank": 4, "options": ["好き", "好きな", "好きだ", "好きに"], "answer": 0, "grammar": ""}""", "");
        when(geminiClient.generateJson(anyString())).thenReturn(Optional.of(threeQuestions));
        when(passageRepository.save(any(ExamPassage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PassageDraftService.PassageDraftResult result = service.draft("N4");

        assertThat(result.status()).isEqualTo(ExamQuestionStatus.REJECTED);
        assertThat(result.questions()).isEqualTo(3);
        assertThat(savedPassage().getReviewNote()).contains("thừa chỗ trống 【4】").contains("cần 4 chỗ trống");
        assertThat(savedQuestions()).hasSize(3)
                .allSatisfy(question -> assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.REJECTED));
        // Không tốn thêm request giải lại cho đoạn đã bị loại.
        verify(geminiClient, times(1)).generateJson(anyString());
    }

    @Test
    void draft_shouldRefuse_whenGeminiIsNotConfigured_orItsAnswerIsNotAPassage() {
        when(geminiClient.isEnabled()).thenReturn(false, true);
        when(grammarPointRepository.findByJlptLevelOrderByLessonAscIdAsc(JlptLevel.N4)).thenReturn(List.of());
        when(passageRepository.findByJlptLevelOrderByIdDesc(eq(JlptLevel.N4), any(Pageable.class))).thenReturn(Page.empty());
        when(geminiClient.generateJson(anyString())).thenReturn(Optional.of("[1, 2]"));

        assertThatThrownBy(() -> service.draft("N4"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("GEMINI_API_KEY");
        assertThatThrownBy(() -> service.draft("N4"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Không đọc được đoạn văn");
        verify(passageRepository, never()).save(any());
        verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    void normalizeMarkers_shouldTurnLooseBlanksIntoNumberedBrackets() {
        assertThat(PassageDraftService.normalizeMarkers("a【１】b[2]c［ 3 ］d【4】e【１０】"))
                .isEqualTo("a【1】b【2】c【3】d【4】e【10】");
    }

    private void givenTheLevelHasGrammarAndAPassage() {
        when(geminiClient.isEnabled()).thenReturn(true);
        when(grammarPointRepository.findByJlptLevelOrderByLessonAscIdAsc(JlptLevel.N4)).thenReturn(List.of(AFTER));
        when(passageRepository.findByJlptLevelOrderByIdDesc(eq(JlptLevel.N4), any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(ExamPassage.builder().id(1L).jlptLevel(JlptLevel.N4).title("駅までの道").content("...").build())));
    }

    private ExamPassage savedPassage() {
        ArgumentCaptor<ExamPassage> captor = ArgumentCaptor.forClass(ExamPassage.class);
        verify(passageRepository).save(captor.capture());
        return captor.getValue();
    }

    private List<ExamQuestion> savedQuestions() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }
}
