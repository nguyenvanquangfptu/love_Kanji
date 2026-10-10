package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamMondaiResponse;
import com.kanjimastery.backend.dto.JlptLevelResponse;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamSitting;
import com.kanjimastery.backend.model.ExamSittingStatus;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSittingRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.stream.IntStream;

import static com.kanjimastery.backend.model.JlptQuestionType.CONTEXT;
import static com.kanjimastery.backend.model.JlptQuestionType.GRAMMAR_FORM;
import static com.kanjimastery.backend.model.JlptQuestionType.KANJI_READING;
import static com.kanjimastery.backend.model.JlptQuestionType.ORTHOGRAPHY;
import static com.kanjimastery.backend.model.JlptQuestionType.PARAPHRASE;
import static com.kanjimastery.backend.model.JlptQuestionType.SENTENCE_ORDER;
import static com.kanjimastery.backend.model.JlptQuestionType.TEXT_GRAMMAR;
import static com.kanjimastery.backend.model.JlptQuestionType.USAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JlptExamServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long SITTING_ID = 9L;

    @Spy
    private JlptBlueprintProperties blueprints = new JlptBlueprintProperties();
    @Mock
    private JlptExamAssembler assembler;
    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private ExamSittingRepository sittingRepository;
    @Mock
    private UserExamAttemptRepository attemptRepository;
    @Mock
    private UserExamAnswerRepository answerRepository;
    @Mock
    private ExamService examService;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private JlptExamService jlptExamService;

    private JlptBlueprintProperties.Section vocabulary;
    private JlptBlueprintProperties.Section grammar;

    /** Cấu trúc đề N4 thật: Từ vựng 28 câu 25 phút, Ngữ pháp 21 câu 20 phút. */
    @BeforeEach
    void setUp() {
        vocabulary = section(ExamSection.VOCABULARY, 25);
        vocabulary.getQuestions().put(KANJI_READING, 7);
        vocabulary.getQuestions().put(ORTHOGRAPHY, 5);
        vocabulary.getQuestions().put(CONTEXT, 8);
        vocabulary.getQuestions().put(PARAPHRASE, 4);
        vocabulary.getQuestions().put(USAGE, 4);
        grammar = section(ExamSection.GRAMMAR, 20);
        grammar.getQuestions().put(GRAMMAR_FORM, 13);
        grammar.getQuestions().put(SENTENCE_ORDER, 4);
        grammar.getQuestions().put(TEXT_GRAMMAR, 4);
        JlptBlueprintProperties.Level n4 = new JlptBlueprintProperties.Level();
        n4.setSections(List.of(vocabulary, grammar));
        blueprints.getLevels().put(JlptLevel.N4, n4);
    }

    @Test
    void durationSeconds_shouldFollowTheRealTest_andShrinkWithTheNumberOfQuestions() {
        assertThat(JlptExamService.durationSeconds(vocabulary, 28)).isEqualTo(25 * 60);
        // 25 phút x 20/28 = 17,9 phút -> 18 phút.
        assertThat(JlptExamService.durationSeconds(vocabulary, 20)).isEqualTo(18 * 60);
        assertThat(JlptExamService.durationSeconds(vocabulary, 1)).isEqualTo(60);
        assertThat(JlptExamService.durationSeconds(vocabulary, 0)).isZero();
    }

    @Test
    void sectionMinutes_shouldKeepTheChosenTimes_inTheOrderOfTheTest() {
        List<ExamSection> both = List.of(ExamSection.VOCABULARY, ExamSection.GRAMMAR);
        Map<String, Integer> minutes = new LinkedHashMap<>();
        minutes.put("grammar", 30);
        minutes.put("VOCABULARY", 45);

        assertThat(JlptExamService.sectionMinutes(minutes, both)).isEqualTo("VOCABULARY:45,GRAMMAR:30");
        // Không ghi số phút = theo giờ đề thật.
        Map<String, Integer> standardGrammar = new LinkedHashMap<>();
        standardGrammar.put("VOCABULARY", 40);
        standardGrammar.put("GRAMMAR", null);
        assertThat(JlptExamService.sectionMinutes(standardGrammar, both)).isEqualTo("VOCABULARY:40");
        assertThat(JlptExamService.sectionMinutes(null, both)).isNull();
        assertThat(JlptExamService.sectionMinutes(Map.of(), both)).isNull();
    }

    @Test
    void sectionMinutes_shouldRefuseTimesOutOfRange_orForAPartNotChosen() {
        List<ExamSection> vocabularyOnly = List.of(ExamSection.VOCABULARY);

        assertThatThrownBy(() -> JlptExamService.sectionMinutes(Map.of("VOCABULARY", 4), vocabularyOnly))
                .isInstanceOf(BadRequestException.class).hasMessage("Thời gian mỗi phần từ 5 đến 120 phút");
        assertThatThrownBy(() -> JlptExamService.sectionMinutes(Map.of("VOCABULARY", 121), vocabularyOnly))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> JlptExamService.sectionMinutes(Map.of("GRAMMAR", 30), vocabularyOnly))
                .isInstanceOf(BadRequestException.class).hasMessage("Chỉ đặt giờ được cho phần đã chọn: GRAMMAR");
    }

    @Test
    void levels_shouldTellHowManyQuestionsAndMinutesAnExamWouldHaveNow() {
        when(questionRepository.countApprovedByType("N4", null)).thenReturn(List.of(
                new Count(KANJI_READING, 300L), new Count(ORTHOGRAPHY, 3L), new Count(CONTEXT, 200L)));

        List<JlptLevelResponse> levels = jlptExamService.levels(null);

        assertThat(levels).singleElement().extracting(JlptLevelResponse::getJlptLevel).isEqualTo(JlptLevel.N4);
        JlptLevelResponse.Section words = levels.get(0).getSections().get(0);
        // 7 + 3 (mới có 3 câu 表記) + 8 = 18 câu; 25 phút x 18/28 = 16 phút.
        assertThat(words.getQuestionCount()).isEqualTo(18);
        assertThat(words.getMinutes()).isEqualTo(16);
        assertThat(words.getPlannedQuestions()).isEqualTo(28);
        assertThat(words.getPlannedMinutes()).isEqualTo(25);
        assertThat(words.getMondai())
                .extracting(JlptLevelResponse.Mondai::getNumber, JlptLevelResponse.Mondai::getType,
                        JlptLevelResponse.Mondai::getPlannedCount, JlptLevelResponse.Mondai::getAvailable)
                .containsExactly(tuple(1, KANJI_READING, 7, 300), tuple(2, ORTHOGRAPHY, 5, 3),
                        tuple(3, CONTEXT, 8, 200), tuple(4, PARAPHRASE, 4, 0), tuple(5, USAGE, 4, 0));
        JlptLevelResponse.Section grammarSection = levels.get(0).getSections().get(1);
        assertThat(grammarSection.getQuestionCount()).isZero();
        assertThat(grammarSection.getMinutes()).isZero();
    }

    @Test
    void startSitting_shouldTakeTheChosenSectionsInTheRealOrder_andStartTheFirstWithTimeForItsQuestions() {
        when(questionRepository.countApprovedByType("N4", null)).thenReturn(List.of(
                new Count(KANJI_READING, 300L), new Count(CONTEXT, 200L), new Count(GRAMMAR_FORM, 50L)));
        when(sittingRepository.save(any(ExamSitting.class))).thenAnswer(invocation -> {
            ExamSitting sitting = invocation.getArgument(0);
            sitting.setId(SITTING_ID);
            return sitting;
        });
        List<ExamQuestion> reading = questions(1, 7);
        List<ExamQuestion> context = questions(101, 8);
        when(assembler.assemble(eq(USER_ID), eq(JlptLevel.N4), eq(vocabulary), any(), any())).thenReturn(List.of(
                new JlptExamAssembler.Mondai(1, KANJI_READING, 7, reading),
                new JlptExamAssembler.Mondai(2, ORTHOGRAPHY, 5, List.of()),
                new JlptExamAssembler.Mondai(3, CONTEXT, 8, context),
                new JlptExamAssembler.Mondai(4, PARAPHRASE, 4, List.of()),
                new JlptExamAssembler.Mondai(5, USAGE, 4, List.of())));
        StartExamResponse started = StartExamResponse.builder().attemptId(30L).build();
        ArgumentCaptor<UserExamAttempt> attempt = ArgumentCaptor.forClass(UserExamAttempt.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamQuestion>> questions = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamMondaiResponse>> mondai = ArgumentCaptor.forClass(List.class);
        when(examService.begin(attempt.capture(), questions.capture(), mondai.capture())).thenReturn(started);

        assertThat(jlptExamService.startSitting(USER_ID, request("n4", "GRAMMAR", "vocabulary"))).isSameAs(started);

        ArgumentCaptor<ExamSitting> sitting = ArgumentCaptor.forClass(ExamSitting.class);
        verify(sittingRepository).save(sitting.capture());
        assertThat(sitting.getValue().getSections()).isEqualTo("VOCABULARY,GRAMMAR");
        assertThat(sitting.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(sitting.getValue().getJlptLevel()).isEqualTo(JlptLevel.N4);
        // 15 câu thay vì 28: 25 phút x 15/28 = 13 phút.
        assertThat(attempt.getValue().getSittingId()).isEqualTo(SITTING_ID);
        assertThat(attempt.getValue().getSection()).isEqualTo(ExamSection.VOCABULARY);
        assertThat(attempt.getValue().getDurationSeconds()).isEqualTo(13 * 60);
        assertThat(attempt.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(questions.getValue()).hasSize(15).startsWith(reading.get(0)).endsWith(context.get(7));
        assertThat(mondai.getValue())
                .extracting(ExamMondaiResponse::getNumber, ExamMondaiResponse::getType,
                        ExamMondaiResponse::getQuestionCount, ExamMondaiResponse::getPlannedCount)
                .containsExactly(tuple(1, KANJI_READING, 7, 7), tuple(3, CONTEXT, 8, 8));
    }

    @Test
    void startSitting_shouldRefuseUnknownLevelsAndSections_andSectionsWithoutQuestions() {
        assertThatThrownBy(() -> jlptExamService.startSitting(USER_ID, request("N1", "VOCABULARY")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> jlptExamService.startSitting(USER_ID, request("N4", "LISTENING")))
                .isInstanceOf(BadRequestException.class);

        when(questionRepository.countApprovedByType("N4", null)).thenReturn(List.of(new Count(KANJI_READING, 300L)));
        assertThatThrownBy(() -> jlptExamService.startSitting(USER_ID, request("N4", "VOCABULARY", "GRAMMAR")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngữ pháp");
        verify(sittingRepository, never()).save(any());
    }

    @Test
    void startNextSection_shouldWaitForTheRunningSection_thenStartTheNextWithoutAskingTheSameWordsAgain() {
        when(sittingRepository.findByIdForUpdate(SITTING_ID)).thenReturn(Optional.of(sitting(ExamSittingStatus.IN_PROGRESS)));
        when(attemptRepository.findBySittingIdOrderByIdAsc(SITTING_ID)).thenReturn(
                List.of(attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.IN_PROGRESS)),
                List.of(attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.TIMEOUT)));

        assertThatThrownBy(() -> jlptExamService.startNextSection(USER_ID, SITTING_ID))
                .isInstanceOf(BadRequestException.class);

        when(answerRepository.findByAttemptIdOrderByIdAsc(30L)).thenReturn(List.of(answer(1L), answer(2L)));
        when(questionRepository.findAllWithWordsByIdIn(List.of(1L, 2L))).thenReturn(List.of(
                ExamQuestion.builder().id(1L).kanjiIds(Set.of(10L)).build(),
                ExamQuestion.builder().id(2L).kanjiIds(Set.of(11L)).build()));
        when(assembler.assemble(USER_ID, JlptLevel.N4, grammar, Set.of(10L, 11L), null)).thenReturn(List.of(
                new JlptExamAssembler.Mondai(1, GRAMMAR_FORM, 13, questions(201, 1))));
        ArgumentCaptor<UserExamAttempt> attempt = ArgumentCaptor.forClass(UserExamAttempt.class);
        when(examService.begin(attempt.capture(), any(), any())).thenReturn(StartExamResponse.builder().build());

        jlptExamService.startNextSection(USER_ID, SITTING_ID);

        assertThat(attempt.getValue().getSection()).isEqualTo(ExamSection.GRAMMAR);
        // Một câu thay vì 21: ít nhất 1 phút.
        assertThat(attempt.getValue().getDurationSeconds()).isEqualTo(60);
    }

    @Test
    void startNextSection_shouldRefuseOtherUsersSittings_finishedSittings_andSittingsWithNothingLeft() {
        ExamSitting someoneElses = sitting(ExamSittingStatus.IN_PROGRESS);
        someoneElses.setUserId(8L);
        when(sittingRepository.findByIdForUpdate(SITTING_ID)).thenReturn(
                Optional.of(someoneElses), Optional.of(sitting(ExamSittingStatus.ABANDONED)),
                Optional.of(sitting(ExamSittingStatus.IN_PROGRESS)));
        when(attemptRepository.findBySittingIdOrderByIdAsc(SITTING_ID)).thenReturn(List.of(
                attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.COMPLETED),
                attempt(31L, ExamSection.GRAMMAR, ExamAttemptStatus.COMPLETED)));

        assertThatThrownBy(() -> jlptExamService.startNextSection(USER_ID, SITTING_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> jlptExamService.startNextSection(USER_ID, SITTING_ID))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> jlptExamService.startNextSection(USER_ID, SITTING_ID))
                .isInstanceOf(BadRequestException.class);
        verify(examService, never()).begin(any(), any(), any());
    }

    @Test
    void getSitting_shouldEstimateAScoreOutOf60_fromTheSectionsAlreadyScored() {
        when(sittingRepository.findById(SITTING_ID)).thenReturn(Optional.of(sitting(ExamSittingStatus.IN_PROGRESS)));
        UserExamAttempt vocabularyDone = attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.COMPLETED);
        vocabularyDone.setTotalScore(20);
        UserExamAttempt grammarDone = attempt(31L, ExamSection.GRAMMAR, ExamAttemptStatus.TIMEOUT);
        grammarDone.setTotalScore(10);
        when(attemptRepository.findBySittingIdOrderByIdAsc(SITTING_ID))
                .thenReturn(List.of(vocabularyDone), List.of(vocabularyDone, grammarDone));
        when(answerRepository.countByAttemptId(30L)).thenReturn(28L);
        when(answerRepository.countByAttemptId(31L)).thenReturn(21L);

        // Mới xong phần Từ vựng: 20/28 x 60 = 42,9 -> 43.
        assertThat(jlptExamService.getSitting(USER_ID, SITTING_ID).getEstimatedScore()).isEqualTo(43);
        // Xong cả hai phần: 30/49 x 60 = 36,7 -> 37.
        assertThat(jlptExamService.getSitting(USER_ID, SITTING_ID).getEstimatedScore()).isEqualTo(37);
        assertThat(JlptExamService.estimatedScore(0, 21)).isZero();
        assertThat(JlptExamService.estimatedScore(21, 21)).isEqualTo(60);
    }

    @Test
    void onSectionFinished_shouldCompleteTheSittingOnlyAfterItsLastSection_andReportTheWholeSitting() {
        when(sittingRepository.findById(SITTING_ID)).thenReturn(Optional.of(sitting(ExamSittingStatus.IN_PROGRESS)));
        UserExamAttempt vocabulary = attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.TIMEOUT);
        vocabulary.setTotalScore(20);
        vocabulary.setTimeSpentSeconds(1510);
        vocabulary.setDurationSeconds(1500);
        UserExamAttempt grammar = attempt(31L, ExamSection.GRAMMAR, ExamAttemptStatus.COMPLETED);
        grammar.setTotalScore(15);
        grammar.setTimeSpentSeconds(900);
        grammar.setDurationSeconds(1200);
        when(attemptRepository.findBySittingIdOrderByIdAsc(SITTING_ID))
                .thenReturn(List.of(vocabulary), List.of(vocabulary, grammar));
        when(sittingRepository.finishIfInProgress(eq(SITTING_ID), eq(ExamSittingStatus.COMPLETED),
                any(LocalDateTime.class))).thenReturn(1);
        when(answerRepository.countByAttemptId(30L)).thenReturn(28L);
        when(answerRepository.countByAttemptId(31L)).thenReturn(21L);

        assertThat(jlptExamService.onSectionFinished(SITTING_ID)).isEmpty();
        verify(sittingRepository, never()).finishIfInProgress(anyLong(), any(), any());

        // Phần Từ vựng tự nộp trễ 10 giây sau khi hết giờ: chỉ tính đủ 25 phút.
        assertThat(jlptExamService.onSectionFinished(SITTING_ID))
                .contains(new JlptExamService.CompletedSitting(USER_ID, JlptLevel.N4, 35, 49, 1500 + 900));
    }

    @Test
    void onSectionFinished_shouldNotReportSittingsWithoutEverySectionOfTheLevel() {
        ExamSitting grammarOnly = sitting(ExamSittingStatus.IN_PROGRESS);
        grammarOnly.setSections("GRAMMAR");
        when(sittingRepository.findById(SITTING_ID)).thenReturn(Optional.of(grammarOnly));
        when(attemptRepository.findBySittingIdOrderByIdAsc(SITTING_ID))
                .thenReturn(List.of(attempt(31L, ExamSection.GRAMMAR, ExamAttemptStatus.COMPLETED)));
        when(sittingRepository.finishIfInProgress(eq(SITTING_ID), eq(ExamSittingStatus.COMPLETED),
                any(LocalDateTime.class))).thenReturn(1);

        // Buổi thi vẫn hoàn thành, nhưng chỉ một phần nên không lên bảng xếp hạng đề JLPT.
        assertThat(jlptExamService.onSectionFinished(SITTING_ID)).isEmpty();
        verify(sittingRepository).finishIfInProgress(eq(SITTING_ID), eq(ExamSittingStatus.COMPLETED),
                any(LocalDateTime.class));
    }

    @Test
    void closeStale_shouldAbandonUnfinishedSittings_completeFinishedOnes_andLeaveRunningSectionsAlone() {
        when(sittingRepository.findByIdForUpdate(SITTING_ID)).thenReturn(Optional.of(sitting(ExamSittingStatus.IN_PROGRESS)));
        when(attemptRepository.findBySittingIdOrderByIdAsc(SITTING_ID)).thenReturn(
                List.of(attempt(31L, ExamSection.GRAMMAR, ExamAttemptStatus.IN_PROGRESS)),
                List.of(attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.COMPLETED)),
                List.of(attempt(30L, ExamSection.VOCABULARY, ExamAttemptStatus.COMPLETED),
                        attempt(31L, ExamSection.GRAMMAR, ExamAttemptStatus.TIMEOUT)));

        jlptExamService.closeStale(SITTING_ID);
        verify(sittingRepository, never()).finishIfInProgress(anyLong(), any(), any());

        jlptExamService.closeStale(SITTING_ID);
        verify(sittingRepository).finishIfInProgress(eq(SITTING_ID), eq(ExamSittingStatus.ABANDONED), any());

        jlptExamService.closeStale(SITTING_ID);
        verify(sittingRepository).finishIfInProgress(eq(SITTING_ID), eq(ExamSittingStatus.COMPLETED), any());
    }

    private record Count(JlptQuestionType getType, Long getCount) implements ExamQuestionRepository.TypeCount {
    }

    private static JlptBlueprintProperties.Section section(ExamSection name, int minutes) {
        JlptBlueprintProperties.Section section = new JlptBlueprintProperties.Section();
        section.setName(name);
        section.setMinutes(minutes);
        section.setQuestions(new LinkedHashMap<>());
        return section;
    }

    private static StartJlptExamRequest request(String level, String... sections) {
        StartJlptExamRequest request = new StartJlptExamRequest();
        request.setJlptLevel(level);
        request.setSections(List.of(sections));
        return request;
    }

    private static ExamSitting sitting(ExamSittingStatus status) {
        return ExamSitting.builder().id(SITTING_ID).userId(USER_ID).jlptLevel(JlptLevel.N4).sections("VOCABULARY,GRAMMAR")
                .status(status).startedAt(LocalDateTime.now().minusMinutes(40)).build();
    }

    private static UserExamAttempt attempt(Long id, ExamSection section, ExamAttemptStatus status) {
        return UserExamAttempt.builder().id(id).userId(USER_ID).jlptLevel(JlptLevel.N4).sittingId(SITTING_ID).section(section)
                .status(status).startedAt(LocalDateTime.now().minusMinutes(30)).build();
    }

    private static UserExamAnswer answer(Long questionId) {
        return UserExamAnswer.builder().attemptId(30L).questionId(questionId).selectedOption("A").isCorrect(true).build();
    }

    private static List<ExamQuestion> questions(int firstId, int count) {
        return IntStream.range(firstId, firstId + count)
                .mapToObj(id -> ExamQuestion.builder().id((long) id).kanjiIds(Set.of((long) id)).build())
                .toList();
    }
}
