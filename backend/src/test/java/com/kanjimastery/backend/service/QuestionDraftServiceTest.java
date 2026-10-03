package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionDraftServiceTest {

    private static final GrammarPoint AFTER = GrammarPoint.builder().id(3L).jlptLevel("N5").lesson("N5-16")
            .pattern("Vてから").meaningVi("Sau khi làm V1 rồi mới V2").connection("V1て + から、V2").build();

    @Mock
    private GeminiClient geminiClient;
    @Mock
    private GrammarPointRepository grammarPointRepository;
    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private VocabularyLevelChecker levelChecker;

    private QuestionDraftService service;

    @BeforeEach
    void setUp() {
        service = new QuestionDraftService(geminiClient, new ObjectMapper(), grammarPointRepository, questionRepository,
                levelChecker);
    }

    @Test
    void draft_shouldQueueWellFormedDrafts_rejectBrokenOnes_andFlagWhatTheSecondSolveDisagreesWith() {
        when(geminiClient.isEnabled()).thenReturn(true);
        when(grammarPointRepository.findById(3L)).thenReturn(Optional.of(AFTER));
        when(questionRepository.findSentencesByGrammarPoint(3L)).thenReturn(List.of());
        // Kho từ nhỏ: 運転 là từ N3, dùng trong câu N5 thì vượt cấp.
        when(levelChecker.open("N5")).thenReturn(new VocabularyLevelChecker.Session(5, Map.of(
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
            assertThat(question.getJlptLevel()).isEqualTo("N5");
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
        when(levelChecker.open("N5")).thenReturn(new VocabularyLevelChecker.Session(5, Map.of(
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

    /** Số thứ tự (1-4) của lựa chọn {@code option} trong câu thứ {@code number} của prompt giải lại. */
    private static int option(String prompt, int number, String option) {
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

    private static String correct(ExamQuestion question) {
        return switch (question.getCorrectOption()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            default -> question.getOptionD();
        };
    }
}
