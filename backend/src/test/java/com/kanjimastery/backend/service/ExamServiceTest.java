package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.config.ExamProperties;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamQuestionPublicResponse;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.dto.StartExamRequest;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.ExamQuestionReportRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ATTEMPT_ID = 30L;

    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private UserExamAttemptRepository attemptRepository;
    @Mock
    private UserExamAnswerRepository answerRepository;
    @Mock
    private ExamSessionStore examSessionStore;
    @Mock
    private ExamFinalizationService examFinalizationService;
    @Spy
    private ExamProperties examProperties = new ExamProperties();
    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private GrammarPointRepository grammarPointRepository;
    @Mock
    private ExamQuestionReportRepository reportRepository;
    @Spy
    private JlptBlueprintProperties blueprints = new JlptBlueprintProperties();

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private ExamService examService;

    @Test
    void getReview_shouldScoreEachSkill_inReadingWritingMeaningOrder() {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel(JlptLevel.N5).status(ExamAttemptStatus.COMPLETED).totalScore(3)
                .startedAt(LocalDateTime.now().minusMinutes(20)).build()));
        when(answerRepository.findByAttemptIdOrderByIdAsc(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "B", true), answer(2L, "A", false), answer(3L, null, false), answer(4L, "C", true),
                answer(5L, "D", true)));
        when(questionRepository.findAllWithLinksByIdIn(anyList())).thenReturn(List.of(
                question(1L, QuizDirection.MEANING), question(2L, QuizDirection.KANJI_TO_READING),
                question(3L, QuizDirection.KANJI_TO_READING), question(4L, QuizDirection.KANJI_TO_READING),
                question(5L, null)));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        // Câu chưa phân loại kỹ năng không tính vào kỹ năng nào; kỹ năng không có câu nào thì không hiện.
        assertThat(review.getSkills())
                .extracting(ExamReviewResponse.SkillScore::getSkill, ExamReviewResponse.SkillScore::getCorrect,
                        ExamReviewResponse.SkillScore::getTotal)
                .containsExactly(tuple(QuizDirection.KANJI_TO_READING, 1, 3), tuple(QuizDirection.MEANING, 1, 1));
        assertThat(review.getQuestions().get(0).getSkill()).isEqualTo(QuizDirection.MEANING);
    }

    @Test
    void getReview_shouldListTheWordsOfWrongAnswers_butNotOfSkippedQuestions() {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel(JlptLevel.N5).status(ExamAttemptStatus.TIMEOUT).totalScore(1)
                .startedAt(LocalDateTime.now().minusMinutes(31)).diagnosedAt(LocalDateTime.now()).build()));
        when(answerRepository.findByAttemptIdOrderByIdAsc(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "B", false), answer(2L, null, false), answer(3L, "A", true), answer(4L, "C", false)));
        when(questionRepository.findAllWithLinksByIdIn(anyList())).thenReturn(List.of(
                question(1L, QuizDirection.MEANING, 15L), question(2L, QuizDirection.MEANING, 25L),
                question(3L, QuizDirection.MEANING, 14L), question(4L, QuizDirection.KANJI_TO_READING, 15L)));
        when(kanjiRepository.findAllById(Set.of(15L))).thenReturn(List.of(
                Kanji.builder().id(15L).character("水").reading("みず").meaning("Nước").build()));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        // Câu 2 bỏ trống (hết giờ) không tính; 水 sai ở hai câu chỉ hiện một lần.
        assertThat(review.getWrongWords()).extracting(ExamReviewResponse.Word::getCharacter).containsExactly("水");
        assertThat(review.isAddedToReview()).isTrue();
    }

    @Test
    void start_shouldSpreadQuestionsOverTheSkills_andAskAtMostOneQuestionPerWord() {
        // Câu đọc 2 hỏi lại 水 của câu đọc 1 nên bị bỏ; câu nghĩa 4 cũng hỏi 水. Lấy lần lượt đọc - viết - nghĩa.
        List<ExamQuestion> reading = List.of(question(1L, QuizDirection.KANJI_TO_READING, 15L),
                question(2L, QuizDirection.KANJI_TO_READING, 15L), question(6L, QuizDirection.KANJI_TO_READING, 19L));
        List<ExamQuestion> writing = List.of(question(3L, QuizDirection.READING_TO_KANJI, 16L));
        List<ExamQuestion> meaning = List.of(question(4L, QuizDirection.MEANING, 15L),
                question(5L, QuizDirection.MEANING, 17L), question(7L, QuizDirection.MEANING, 18L));
        when(questionRepository.findRandomByLevelAndSkill("N5", QuizDirection.KANJI_TO_READING.name(), 8)).thenReturn(reading);
        when(questionRepository.findRandomByLevelAndSkill("N5", QuizDirection.READING_TO_KANJI.name(), 8)).thenReturn(writing);
        when(questionRepository.findRandomByLevelAndSkill("N5", QuizDirection.MEANING.name(), 8)).thenReturn(meaning);
        when(questionRepository.findRandomUnclassifiedByLevel("N5", 8)).thenReturn(List.of());
        when(questionRepository.findAllWithWordsByIdIn(List.of(1L, 2L, 6L, 3L, 4L, 5L, 7L)))
                .thenReturn(Stream.of(reading, writing, meaning).flatMap(List::stream).toList());
        when(attemptRepository.save(any(UserExamAttempt.class))).thenAnswer(invocation -> {
            UserExamAttempt attempt = invocation.getArgument(0);
            attempt.setId(ATTEMPT_ID);
            return attempt;
        });
        StartExamRequest request = new StartExamRequest();
        request.setJlptLevel("n5");
        request.setQuestionCount(4);

        StartExamResponse response = examService.start(USER_ID, request);

        // Vòng 1: đọc 1, viết 3, nghĩa 5 (4 trùng 水); vòng 2: đọc 6.
        assertThat(response.getQuestions()).extracting(ExamQuestionPublicResponse::getId)
                .containsExactlyInAnyOrder(1L, 3L, 5L, 6L);
        // Thi nhanh: thời gian mặc định, không thuộc buổi làm đề JLPT nào.
        verify(examSessionStore).initSession(eq(ATTEMPT_ID), anyList(), eq(1800));
        assertThat(response.getRemainingSeconds()).isEqualTo(1800);
        assertThat(response.getSittingId()).isNull();
        assertThat(response.getMondai()).isNull();
    }

    @Test
    void getReview_ofAJlptSection_shouldScoreEachMondai_numberedAsInTheRealTest() {
        JlptBlueprintProperties.Section vocabulary = new JlptBlueprintProperties.Section();
        vocabulary.setName(ExamSection.VOCABULARY);
        vocabulary.setMinutes(25);
        vocabulary.setQuestions(new LinkedHashMap<>(Map.of(JlptQuestionType.KANJI_READING, 7)));
        vocabulary.getQuestions().put(JlptQuestionType.ORTHOGRAPHY, 5);
        vocabulary.getQuestions().put(JlptQuestionType.CONTEXT, 8);
        JlptBlueprintProperties.Level n4 = new JlptBlueprintProperties.Level();
        n4.setSections(List.of(vocabulary));
        blueprints.getLevels().put(JlptLevel.N4, n4);
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel(JlptLevel.N4).status(ExamAttemptStatus.COMPLETED).totalScore(2)
                .sittingId(5L).section(ExamSection.VOCABULARY).durationSeconds(900)
                .startedAt(LocalDateTime.now().minusMinutes(10)).build()));
        when(answerRepository.findByAttemptIdOrderByIdAsc(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "A", true), answer(2L, "B", false), answer(3L, "A", true)));
        when(questionRepository.findAllWithLinksByIdIn(anyList())).thenReturn(List.of(
                typed(question(1L, QuizDirection.KANJI_TO_READING), JlptQuestionType.KANJI_READING),
                typed(question(2L, QuizDirection.KANJI_TO_READING), JlptQuestionType.KANJI_READING),
                typed(question(3L, QuizDirection.MEANING), JlptQuestionType.CONTEXT)));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        // 表記 không có câu nào trong bài thì không hiện; 文脈規定 vẫn là 問題3 như đề thật.
        assertThat(review.getMondai())
                .extracting(ExamReviewResponse.MondaiScore::getNumber, ExamReviewResponse.MondaiScore::getType,
                        ExamReviewResponse.MondaiScore::getCorrect, ExamReviewResponse.MondaiScore::getTotal)
                .containsExactly(tuple(1, JlptQuestionType.KANJI_READING, 1, 2), tuple(3, JlptQuestionType.CONTEXT, 1, 1));
        assertThat(review.getSittingId()).isEqualTo(5L);
        assertThat(review.getSection()).isEqualTo(ExamSection.VOCABULARY);
        assertThat(review.getQuestions().get(2).getQuestionType()).isEqualTo(JlptQuestionType.CONTEXT);
    }

    @Test
    void getReview_ofAQuickExam_shouldHaveNoMondaiScores() {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel(JlptLevel.N5).status(ExamAttemptStatus.COMPLETED).totalScore(1)
                .startedAt(LocalDateTime.now().minusMinutes(20)).build()));
        when(answerRepository.findByAttemptIdOrderByIdAsc(ATTEMPT_ID)).thenReturn(List.of(answer(1L, "A", true)));
        when(questionRepository.findAllWithLinksByIdIn(anyList())).thenReturn(List.of(
                typed(question(1L, QuizDirection.KANJI_TO_READING), JlptQuestionType.KANJI_READING)));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        assertThat(review.getMondai()).isEmpty();
        assertThat(review.getSittingId()).isNull();
    }

    @Test
    void getReview_shouldNameTheGrammarPointsEachQuestionTests() {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel(JlptLevel.N4).status(ExamAttemptStatus.COMPLETED).totalScore(0)
                .sittingId(5L).section(ExamSection.GRAMMAR).startedAt(LocalDateTime.now().minusMinutes(10)).build()));
        when(answerRepository.findByAttemptIdOrderByIdAsc(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "B", false), answer(2L, "A", true)));
        ExamQuestion grammarQuestion = typed(question(1L, null), JlptQuestionType.GRAMMAR_FORM);
        grammarQuestion.setGrammarPointIds(Set.of(72L, 3L));
        when(questionRepository.findAllWithLinksByIdIn(anyList())).thenReturn(List.of(grammarQuestion,
                typed(question(2L, QuizDirection.KANJI_TO_READING), JlptQuestionType.KANJI_READING)));
        when(grammarPointRepository.findAllById(Set.of(72L, 3L))).thenReturn(List.of(
                GrammarPoint.builder().id(72L).pattern("Vてから").meaningVi("Sau khi V1 rồi V2").build(),
                GrammarPoint.builder().id(3L).pattern("〜ですか").meaningVi("Câu hỏi có/không").build()));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        assertThat(review.getQuestions().get(0).getGrammarPoints())
                .extracting(QuestionReviewItem.Grammar::id, QuestionReviewItem.Grammar::pattern)
                .containsExactly(tuple(3L, "〜ですか"), tuple(72L, "Vてから"));
        assertThat(review.getQuestions().get(1).getGrammarPoints()).isEmpty();
    }

    private static ExamQuestion typed(ExamQuestion question, JlptQuestionType questionType) {
        question.setQuestionType(questionType);
        return question;
    }

    private static UserExamAnswer answer(Long questionId, String selected, boolean correct) {
        return UserExamAnswer.builder().attemptId(ATTEMPT_ID).questionId(questionId).selectedOption(selected)
                .isCorrect(correct).build();
    }

    private static ExamQuestion question(Long id, QuizDirection skill, Long... kanjiIds) {
        return ExamQuestion.builder().id(id).jlptLevel(JlptLevel.N5).questionText("Câu " + id).optionA("1").optionB("2")
                .optionC("3").optionD("4").correctOption("A").skill(skill).kanjiIds(Set.of(kanjiIds)).build();
    }
}
