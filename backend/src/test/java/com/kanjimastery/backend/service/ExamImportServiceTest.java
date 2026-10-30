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
import static com.kanjimastery.backend.service.ExamImportFixtures.reading;
import static com.kanjimastery.backend.service.ExamImportFixtures.readingPassage;
import static com.kanjimastery.backend.service.ExamImportFixtures.readingQuestion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                grammar(passage("【19】、【20a】と【20b】、【21】、【22】、【23】。")), null);

        ExamImportResponse report = service.importTest(test, false);

        assertThat(report.errors()).isEmpty();
        ArgumentCaptor<ExamPassage> passage = ArgumentCaptor.forClass(ExamPassage.class);
        verify(passageRepository).save(passage.capture());
        assertThat(passage.getValue().getContent()).isEqualTo("【1】、【2a】と【2b】、【3】、【4】、【5】。");
    }

    @Test
    void passage_shouldReportItsBlanks_inTheNumbersOfTheTest() {
        ExamImportRequest test = new ExamImportRequest("N3-05", "N3", null,
                grammar(passage("【19】、【20】、【21】、【22】、【24】。")), null);

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

    @Test
    void questionsWithoutExplanation_shouldBeReported_namingTheFirstFew() {
        ExamImportResponse report = service.importTest(n3Test("N3-05"), true);

        // Câu sắp xếp có sẵn giải thích dựng từ thứ tự đúng; 53 câu còn lại chưa có "giai_thich".
        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).contains("53 câu chưa có \"giai_thich\" (TV câu 1, TV câu 2, TV câu 3, TV câu 4,"
                + " TV câu 5...) - người học xem lại bài sẽ không thấy giải thích");
    }

    @Test
    void questionsWithExplanation_shouldNotBeReported() {
        ExamImportRequest test = n3Test("N3-05");
        List.of(test.vocabulary().kanjiReading(), test.vocabulary().orthography(), test.vocabulary().context(),
                        test.vocabulary().paraphrase(), test.vocabulary().usage(), test.grammar().grammarForm(),
                        test.grammar().passage().questions())
                .forEach(items -> items.replaceAll(ExamImportServiceTest::withExplanation));

        ExamImportResponse report = service.importTest(test, false);

        assertThat(report.warnings()).noneMatch(warning -> warning.contains("giai_thich"));
        assertThat(byRef(savedQuestions(), "N3-05/TV/1").getExplanation()).isEqualTo("Giải thích câu 1");
    }

    @Test
    void sentenceOrder_shouldPutTheBuiltAnswerFirst_andTheOwnExplanationOnTheNextLine() {
        ExamImportRequest test = n3Test("N3-05");
        test.grammar().sentenceOrder().replaceAll(ExamImportServiceTest::withExplanation);

        service.importTest(test, false);

        assertThat(byRef(savedQuestions(), "N3-05/NP/14").getExplanation()).isEqualTo("Câu đúng: 今日は二14四14三14一14。"
                + " (二14 → 四14 → 三14 → 一14). Vế ở vị trí ★ là 「三14」.\nGiải thích câu 14");
    }

    private static Item withExplanation(Item item) {
        return new Item(item.number(), item.sentence(), item.highlight(), item.keyword(), item.before(), item.after(),
                item.options(), item.answer(), item.order(), item.star(), item.word(), item.grammarPattern(),
                "Giải thích câu " + item.number());
    }

    @Test
    void reading_shouldStoreEachPassageWithItsQuestions_numberedAfterGrammarAsInTheTest() {
        long[] nextId = {100};
        when(passageRepository.save(any(ExamPassage.class))).thenAnswer(invocation -> {
            ExamPassage passage = invocation.getArgument(0);
            passage.setId(nextId[0]++);
            return passage;
        });

        ExamImportResponse report = service.importTest(new ExamImportRequest("N3-05", "N3", null, null, reading()), false);

        assertThat(report.errors()).isEmpty();
        assertThat(report.questions()).isEqualTo(16);
        assertThat(report.passages()).isEqualTo(8);
        ArgumentCaptor<ExamPassage> passages = ArgumentCaptor.forClass(ExamPassage.class);
        verify(passageRepository, times(8)).save(passages.capture());
        assertThat(passages.getAllValues()).allSatisfy(passage -> {
            assertThat(passage.getSection()).isEqualTo(ExamSection.READING);
            assertThat(passage.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
            assertThat(passage.getSource()).isEqualTo(ExamQuestionSource.IMPORTED);
        });
        assertThat(passages.getAllValues()).extracting(ExamPassage::getSourceRef).containsExactly(
                "N3-05/DH/24", "N3-05/DH/25", "N3-05/DH/26", "N3-05/DH/27", "N3-05/DH/28-30", "N3-05/DH/31-33",
                "N3-05/DH/34-37", "N3-05/DH/38-39");

        List<ExamQuestion> saved = savedQuestions();
        assertThat(saved).extracting(ExamQuestion::getSourceRef).startsWith("N3-05/DH/24").endsWith("N3-05/DH/39");
        // Câu 31-33 là bài trung thứ hai: câu 1..3 của bài đó.
        assertThat(saved).filteredOn(question -> question.getSourceRef().matches("N3-05/DH/3[123]"))
                .extracting(ExamQuestion::getQuestionType, ExamQuestion::getPassageId, ExamQuestion::getBlankNo)
                .containsExactly(tuple(JlptQuestionType.READING_MID, 105L, 1), tuple(JlptQuestionType.READING_MID, 105L, 2),
                        tuple(JlptQuestionType.READING_MID, 105L, 3));
        ExamQuestion last = byRef(saved, "N3-05/DH/39");
        assertThat(last.getQuestionType()).isEqualTo(JlptQuestionType.INFO_SEARCH);
        assertThat(last.getQuestionText()).isEqualTo("問39：筆者の考えに合うものはどれか。");
        assertThat(last.getSentence()).isNull();
        assertThat(last.getCorrectOption()).isEqualTo("B");
    }

    @Test
    void reading_shouldReportMissingPassages_wrongSizes_andNumbering() {
        ExamImportRequest.ReadingPart full = reading();
        ExamImportRequest.ReadingPart broken = new ExamImportRequest.ReadingPart(
                full.shortPassages().subList(0, 3),
                List.of(readingPassage(27, 3), readingPassage(30, 2)),
                full.longPassages(),
                List.of(new ExamImportRequest.Passage(null, "表がない文章。", List.of(readingQuestion(37),
                        readingQuestion(38)))));

        ExamImportResponse report = service.importTest(new ExamImportRequest("N3-05", "N3", null, null, broken), true);

        assertThat(report.errors()).contains(
                "DH 問題4 (READING_SHORT): cần 4 bài, file có 3",
                "DH bài câu 30-31: bài đọc dạng READING_MID cần 3 câu hỏi, đang có 2",
                "DH bài câu 32-35: câu thứ 1 phải là câu 32, file ghi 34",
                "DH bài câu 36-37: bài 情報検索 cần có bảng (các dòng | ô | ô |) hoặc mục liệt kê (dòng bắt đầu bằng ・ ■ ● ※...)");
    }

    @Test
    void readingPassage_shouldImportOneLoosePassageOfTheChosenType() {
        ExamPassageImportRequest request = new ExamPassageImportRequest("N3", "DH-trung-01", "ごみの出し方",
                "〔下線〕ごみ〔/下線〕は朝出す。", List.of(readingQuestion(1), readingQuestion(2), readingQuestion(3)));

        ExamImportResponse report = service.importPassage(JlptQuestionType.READING_MID, request, false);

        assertThat(report.errors()).isEmpty();
        assertThat(report.passages()).isEqualTo(1);
        ArgumentCaptor<ExamPassage> passage = ArgumentCaptor.forClass(ExamPassage.class);
        verify(passageRepository).save(passage.capture());
        assertThat(passage.getValue().getSection()).isEqualTo(ExamSection.READING);
        assertThat(passage.getValue().getSourceRef()).isEqualTo("DH-trung-01/DH/1-3");
        assertThat(savedQuestions()).extracting(ExamQuestion::getSourceRef, ExamQuestion::getPassageId,
                        ExamQuestion::getBlankNo)
                .containsExactly(tuple("DH-trung-01/DH/1", PASSAGE_ID, 1), tuple("DH-trung-01/DH/2", PASSAGE_ID, 2),
                        tuple("DH-trung-01/DH/3", PASSAGE_ID, 3));
    }

    @Test
    void readingPassage_shouldCheckTheSizeOfTheChosenType_andSkipAPassageAlreadyInTheBank() {
        ExamPassageImportRequest twoQuestions = new ExamPassageImportRequest("N3", null, null, "本文。",
                List.of(readingQuestion(1), readingQuestion(2)));

        assertThat(service.importPassage(JlptQuestionType.READING_MID, twoQuestions, true).errors())
                .containsExactly("DH bài câu 1-2: bài đọc dạng READING_MID cần 3 câu hỏi, đang có 2");
        assertThatThrownBy(() -> service.importPassage(JlptQuestionType.TEXT_GRAMMAR, twoQuestions, true))
                .isInstanceOf(BadRequestException.class);

        // Không có mã bài: bài cùng nội dung đã có trong kho thì bỏ qua cả bài.
        when(passageRepository.existsByJlptLevelAndSectionAndContent(JlptLevel.N3, ExamSection.READING, "本文。"))
                .thenReturn(true);
        ExamImportResponse report = service.importPassage(JlptQuestionType.READING_MID,
                new ExamPassageImportRequest("N3", null, null, "本文。",
                        List.of(readingQuestion(1), readingQuestion(2), readingQuestion(3))), false);
        assertThat(report.errors()).isEmpty();
        assertThat(report.passages()).isZero();
        assertThat(report.questions()).isZero();
        assertThat(report.alreadyImported()).isEqualTo(3);
        verify(questionRepository).saveAll(List.of());
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
