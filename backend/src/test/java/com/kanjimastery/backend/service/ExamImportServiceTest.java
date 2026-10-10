package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamImportRequest;
import com.kanjimastery.backend.dto.ExamImportRequest.Item;
import com.kanjimastery.backend.dto.ExamImportResponse;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.kanjimastery.backend.service.ExamImportFixtures.grammar;
import static com.kanjimastery.backend.service.ExamImportFixtures.item;
import static com.kanjimastery.backend.service.ExamImportFixtures.n3Test;
import static com.kanjimastery.backend.service.ExamImportFixtures.options;
import static com.kanjimastery.backend.service.ExamImportFixtures.passage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamImportServiceTest {

    private static final long PASSAGE_ID = 77L;

    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private ExamPassageRepository passageRepository;
    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private GrammarPointRepository grammarPointRepository;

    private ExamImportService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new ExamImportService(questionRepository, passageRepository, kanjiRepository, grammarPointRepository,
                blueprints(), Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneOffset.UTC));
        lenient().when(passageRepository.save(any(ExamPassage.class))).thenAnswer(invocation -> {
            ExamPassage passage = invocation.getArgument(0);
            passage.setId(PASSAGE_ID);
            return passage;
        });
    }

    @Test
    void dryRun_shouldCheckAWholeTest_withoutWritingAnything() {
        ExamImportResponse report = service.importTest(n3Test("N3-05"), true);

        assertThat(report.errors()).isEmpty();
        assertThat(report.questions()).isEqualTo(58);
        assertThat(report.passages()).isEqualTo(1);
        assertThat(report.imported()).isFalse();
        verify(questionRepository, never()).saveAll(any());
        verify(passageRepository, never()).save(any());
    }

    @Test
    void import_shouldStoreEveryQuestionAsAnImportedDraft_inTheOrderOfTheTest() {
        ExamImportResponse report = service.importTest(n3Test("N3-05"), false);

        assertThat(report.imported()).isTrue();
        List<ExamQuestion> saved = savedQuestions();
        assertThat(saved).hasSize(58).allSatisfy(question -> {
            assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.IMPORTED);
            assertThat(question.getJlptLevel()).isEqualTo(JlptLevel.N3);
        });
        assertThat(saved).extracting(ExamQuestion::getSourceRef)
                .startsWith("N3-05/TV/1", "N3-05/TV/2")
                .endsWith("N3-05/NP/22", "N3-05/NP/23");

        ExamQuestion reading = byRef(saved, "N3-05/TV/1");
        assertThat(reading.getQuestionType()).isEqualTo(JlptQuestionType.KANJI_READING);
        assertThat(reading.getSkill()).isEqualTo(QuizDirection.KANJI_TO_READING);
        assertThat(reading.getHighlight()).isEqualTo("語1");
        assertThat(reading.getOptionA()).isEqualTo("一1");
        assertThat(reading.getCorrectOption()).isEqualTo("A");
        // Ô trống chép tay （  ） được đổi về ô chuẩn.
        assertThat(byRef(saved, "N3-05/TV/15").getSentence()).isEqualTo("これは（　　）15です。");
        assertThat(byRef(saved, "N3-05/TV/31").getQuestionText()).contains("語31");

        ExamQuestion order = byRef(saved, "N3-05/NP/14");
        assertThat(order.getQuestionType()).isEqualTo(JlptQuestionType.SENTENCE_ORDER);
        assertThat(order.getSentence()).isEqualTo("今日は ＿＿＿ ＿＿＿ ＿★＿ ＿＿＿ 。");
        assertThat(order.getCorrectOption()).as("ô ★ là ô 3, theo thứ tự 2-4-3-1 là lựa chọn 3").isEqualTo("C");
        assertThat(order.getExplanation()).contains("今日は二14四14三14一14。", "Vế ở vị trí ★ là 「三14」");

        ArgumentCaptor<ExamPassage> passage = ArgumentCaptor.forClass(ExamPassage.class);
        verify(passageRepository).save(passage.capture());
        assertThat(passage.getValue().getContent()).isEqualTo("【1】、【2】、【3】、【4】、【5】。");
        assertThat(passage.getValue().getSourceRef()).isEqualTo("N3-05/NP/19-23");
        assertThat(saved.subList(53, 58)).extracting(ExamQuestion::getPassageId, ExamQuestion::getBlankNo,
                        ExamQuestion::getQuestionText)
                .containsExactly(
                        tuple(PASSAGE_ID, 1, "【1】"),
                        tuple(PASSAGE_ID, 2, "【2】"),
                        tuple(PASSAGE_ID, 3, "【3】"),
                        tuple(PASSAGE_ID, 4, "【4】"),
                        tuple(PASSAGE_ID, 5, "【5】"));
    }

    @Test
    void import_shouldWriteNothing_whenAMondaiIsShortOfQuestions() {
        ExamImportRequest test = n3Test("N3-05");
        test.vocabulary().kanjiReading().remove(7);

        ExamImportResponse report = service.importTest(test, false);

        assertThat(report.errors()).contains("TV 問題1 (KANJI_READING): cần 8 câu, file có 7");
        assertThat(report.imported()).isFalse();
        verify(questionRepository, never()).saveAll(any());
        verify(passageRepository, never()).save(any());
    }

    @Test
    void sentenceOrder_shouldBeRefused_whenTheAnswerIsNotThePartUnderTheStar() {
        ExamImportRequest test = n3Test("N3-05");
        // Thứ tự đúng 2-4-3-1 và ★ ở ô 3 thì đáp án là lựa chọn 3, nhưng file ghi 1 (lỗi kiểu câu 15, 17 của đề mẫu).
        test.grammar().sentenceOrder().set(1, new Item(15, null, null, null, "今日は", "。", options(15), 1,
                List.of(2, 4, 3, 1), 3, null, null, null));

        ExamImportResponse report = service.importTest(test, true);

        assertThat(report.errors()).containsExactly(
                "NP câu 15: theo thứ tự đúng, ô ★ (ô 3) là lựa chọn 3 「三15」 nhưng \"dung\" ghi 1");
    }

    @Test
    void underlinedQuestions_shouldBeRefused_withoutTheirUnderlinedPart() {
        ExamImportRequest test = n3Test("N3-05");
        test.vocabulary().paraphrase().set(0, item(26, "めずらしいくだものを食べた。", null, null,
                List.of("よくある", "ときどきある", "あまりない", "どこにもない"), 3, null, null));

        assertThat(service.importTest(test, true).errors()).containsExactly("TV câu 26: câu phải chứa phần được gạch chân");
    }

    @Test
    void options_shouldLoseTheNumbersCopiedFromThePaperTest() {
        ExamImportRequest test = n3Test("N3-05");
        test.vocabulary().kanjiReading().set(0, item(1, "社長は、私の失敗を許してくれた。", "許して", null,
                List.of("1 こわして", "2 とおして", "3 なおして", "4 ゆるして"), 4, null, null));

        service.importTest(test, false);

        ExamQuestion first = savedQuestions().get(0);
        assertThat(List.of(first.getOptionA(), first.getOptionB(), first.getOptionC(), first.getOptionD()))
                .containsExactly("こわして", "とおして", "なおして", "ゆるして");
        assertThat(first.getCorrectOption()).isEqualTo("D");
    }

    @Test
    void passage_shouldTakeBlanksNumberedAsInTheTest_includingAPairForOneQuestion() {
        ExamImportRequest test = new ExamImportRequest("N3-05", "N3", null,
                grammar(passage("【19】、【20a】と【20b】、【21】、【22】、【23】。")));

        ExamImportResponse report = service.importTest(test, false);

        assertThat(report.errors()).isEmpty();
        ArgumentCaptor<ExamPassage> passage = ArgumentCaptor.forClass(ExamPassage.class);
        verify(passageRepository).save(passage.capture());
        assertThat(passage.getValue().getContent()).isEqualTo("【1】、【2a】と【2b】、【3】、【4】、【5】。");
    }

    @Test
    void passage_shouldReportItsBlanks_inTheNumbersOfTheTest() {
        ExamImportRequest test = new ExamImportRequest("N3-05", "N3", null,
                grammar(passage("【19】、【20】、【21】、【22】、【24】。")));

        assertThat(service.importTest(test, true).errors()).containsExactly(
                "NP đoạn văn: chỗ trống 【24】 không ứng với câu nào (câu 19-23)",
                "NP đoạn văn: 【23】 phải có đúng một lần trong đoạn văn");
    }

    @Test
    void reimport_shouldSkipWhatWasImportedBefore() {
        when(questionRepository.findExistingSourceRefs(anyCollection()))
                .thenAnswer(invocation -> new ArrayList<>(invocation.<Collection<String>>getArgument(0)));
        when(passageRepository.existsBySourceRef("N3-05/NP/19-23")).thenReturn(true);

        ExamImportResponse report = service.importTest(n3Test("N3-05"), false);

        assertThat(report.questions()).isZero();
        assertThat(report.passages()).isZero();
        assertThat(report.alreadyImported()).isEqualTo(58);
        assertThat(savedQuestions()).isEmpty();
        verify(passageRepository, never()).save(any());
    }

    @Test
    void words_shouldBeLinkedByTheUnderlinedWord_orByTheDictionaryFormGiven() {
        ExamImportRequest test = n3Test("N3-05");
        test.vocabulary().kanjiReading().set(0, item(1, "社長は、私の失敗を許してくれた。", "許して", null,
                List.of("こわして", "とおして", "なおして", "ゆるして"), 4, "許す", null));
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(
                Kanji.builder().id(501L).character("許").reading("ゆる(す)").build(),
                Kanji.builder().id(502L).character("語2").reading("ご").build()));

        ExamImportResponse report = service.importTest(test, false);

        List<ExamQuestion> saved = savedQuestions();
        assertThat(byRef(saved, "N3-05/TV/1").getKanjiIds()).containsExactly(501L);
        assertThat(byRef(saved, "N3-05/TV/2").getKanjiIds()).containsExactly(502L);
        assertThat(report.warnings()).contains("TV câu 3: chưa gắn được với từ vựng nào (「語3」 không có trong kho từ)"
                + " - thêm \"tu\" (dạng từ điển) nếu muốn câu làm sai được đưa vào ôn tập");
    }

    @Test
    void grammarQuestions_shouldBeLinkedToTheGrammarPointNamed() {
        ExamImportRequest test = n3Test("N3-05");
        test.grammar().grammarForm().set(7, item(8, "1人で10人分の仕事をするなんて（  ）。", null, null,
                List.of("できかねない", "できなくはない", "できっこない", "できないことはない"), 3, null, "~っこない"));
        when(grammarPointRepository.findByJlptLevelAndPattern(JlptLevel.N3, "〜っこない"))
                .thenReturn(Optional.of(GrammarPoint.builder().id(9L).pattern("〜っこない").build()));

        ExamImportResponse report = service.importTest(test, false);

        assertThat(byRef(savedQuestions(), "N3-05/NP/8").getGrammarPointIds()).containsExactly(9L);
        assertThat(report.warnings()).contains("22 câu ngữ pháp chưa ghi \"ngu_phap\" nên chưa gắn với điểm ngữ pháp"
                + " nào - thêm vào nếu muốn dùng cho phần luyện ngữ pháp yếu");
    }

    @SuppressWarnings("unchecked")
    private List<ExamQuestion> savedQuestions() {
        ArgumentCaptor<List<ExamQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static ExamQuestion byRef(List<ExamQuestion> questions, String ref) {
        return questions.stream().filter(question -> ref.equals(question.getSourceRef())).findFirst().orElseThrow();
    }

    private static JlptBlueprintProperties blueprints() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("jlpt-blueprints", new ClassPathResource("jlpt-blueprints.yml"));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("app.jlpt", JlptBlueprintProperties.class)
                .orElseThrow(IllegalStateException::new);
    }
}
