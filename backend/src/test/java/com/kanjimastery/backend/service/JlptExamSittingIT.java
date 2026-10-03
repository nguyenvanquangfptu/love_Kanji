package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamMondaiResponse;
import com.kanjimastery.backend.dto.ExamQuestionPublicResponse;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.dto.ExamSittingResponse;
import com.kanjimastery.backend.dto.SaveAnswerRequest;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.job.ExamReconciliationJob;
import com.kanjimastery.backend.job.ExamSittingCleanupJob;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamSitting;
import com.kanjimastery.backend.model.ExamSittingStatus;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.ExamSittingRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.kanjimastery.backend.model.JlptQuestionType.CONTEXT;
import static com.kanjimastery.backend.model.JlptQuestionType.GRAMMAR_FORM;
import static com.kanjimastery.backend.model.JlptQuestionType.KANJI_READING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Buổi làm đề JLPT hai phần trên PostgreSQL + Redis thật: mỗi phần một đồng hồ riêng, hết giờ phần đầu chỉ chốt phần
 * đó, phần sau không hỏi lại từ đã hỏi, làm xong phần cuối thì buổi thi hoàn thành.
 */
class JlptExamSittingIT extends AbstractIntegrationTest {

    /** Cấp độ giả với cấu trúc đề nhỏ, để không đụng tới đề N5-N3 thật. */
    private static final String LEVEL = "N8";

    @Autowired
    private JlptExamService jlptExamService;
    @Autowired
    private ExamService examService;
    @Autowired
    private ExamFinalizationService examFinalizationService;
    @Autowired
    private LeaderboardService leaderboardService;
    @Autowired
    private ExamReconciliationJob reconciliationJob;
    @Autowired
    private ExamSittingCleanupJob sittingCleanupJob;
    @Autowired
    private JlptBlueprintProperties blueprints;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private ExamSittingRepository sittingRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private ExamSessionStore examSessionStore;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long userId;
    private final List<Long> wordIds = new ArrayList<>();
    private final List<Long> attemptIds = new ArrayList<>();
    private ExamQuestion grammarOnFirstWord;
    private ExamQuestion grammarOnFourthWord;
    private ExamQuestion grammarOnFifthWord;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder()
                .username("jlpt_sitting_" + suffix)
                .email("jlpt_sitting_" + suffix + "@test.local")
                .passwordHash("x")
                .build()).getId();
        for (String[] word : new String[][] {{"新聞", "しんぶん"}, {"学校", "がっこう"}, {"先生", "せんせい"},
                {"病院", "びょういん"}, {"電車", "でんしゃ"}}) {
            wordIds.add(kanjiRepository.save(Kanji.builder().character(word[0]).reading(word[1]).meaning(word[0])
                    .hanViet("").jlptLevel("N5").strokeCount(10).build()).getId());
        }
        // Từ vựng: câu đọc và câu điền từ cho 3 từ đầu; ngữ pháp: 3 câu, câu đầu dính tới từ thứ nhất.
        for (int i = 0; i < 3; i++) {
            question(KANJI_READING, QuizDirection.KANJI_TO_READING, wordIds.get(i));
            question(CONTEXT, QuizDirection.MEANING, wordIds.get(i));
        }
        grammarOnFirstWord = question(GRAMMAR_FORM, null, wordIds.get(0));
        grammarOnFourthWord = question(GRAMMAR_FORM, null, wordIds.get(3));
        grammarOnFifthWord = question(GRAMMAR_FORM, null, wordIds.get(4));

        // Đề N8: Từ vựng 4 câu 10 phút (2 câu đọc, 2 câu điền từ), Ngữ pháp 2 câu 5 phút.
        JlptBlueprintProperties.Section vocabulary = section(ExamSection.VOCABULARY, 10);
        vocabulary.getQuestions().put(KANJI_READING, 2);
        vocabulary.getQuestions().put(CONTEXT, 2);
        JlptBlueprintProperties.Section grammar = section(ExamSection.GRAMMAR, 5);
        grammar.getQuestions().put(GRAMMAR_FORM, 2);
        JlptBlueprintProperties.Level level = new JlptBlueprintProperties.Level();
        level.setSections(List.of(vocabulary, grammar));
        blueprints.getLevels().put(LEVEL, level);
    }

    @AfterEach
    void tearDown() {
        blueprints.getLevels().remove(LEVEL);
        attemptRepository.findAll().stream()
                .filter(attempt -> attempt.getUserId().equals(userId))
                .forEach(attempt -> examSessionStore.cleanup(attempt.getId()));
        attemptIds.forEach(examSessionStore::cleanup);
        // ON DELETE CASCADE dọn buổi thi, lượt thi, câu trả lời, lịch ôn của người dùng thử; liên kết câu - từ đi theo câu.
        userRepository.deleteById(userId);
        questionRepository.deleteAll(questionRepository.findAll().stream()
                .filter(question -> LEVEL.equals(question.getJlptLevel()))
                .toList());
        kanjiRepository.deleteAllById(wordIds);
    }

    @Test
    void aTwoSectionSitting_shouldTimeEachSectionOnItsOwn_andCompleteOnlyAfterTheLastSection() {
        StartExamResponse vocabulary = jlptExamService.startSitting(userId,
                request(ExamSection.GRAMMAR, ExamSection.VOCABULARY));
        Long sittingId = vocabulary.getSittingId();

        // Làm theo thứ tự đề thật dù chọn ngược. 2 câu đọc hỏi 2 trong 3 từ, câu điền từ chỉ còn 1 từ chưa hỏi:
        // 3 câu thay vì 4, nên 10 phút x 3/4 = 7,5 -> 8 phút.
        assertThat(vocabulary.getSection()).isEqualTo(ExamSection.VOCABULARY);
        assertThat(vocabulary.getQuestions()).extracting(ExamQuestionPublicResponse::getQuestionType)
                .containsExactly(KANJI_READING, KANJI_READING, CONTEXT);
        assertThat(vocabulary.getMondai())
                .extracting(ExamMondaiResponse::getNumber, ExamMondaiResponse::getType,
                        ExamMondaiResponse::getQuestionCount, ExamMondaiResponse::getPlannedCount)
                .containsExactly(tuple(1, KANJI_READING, 2, 2), tuple(2, CONTEXT, 1, 2));
        assertThat(vocabulary.getRemainingSeconds()).isEqualTo(8 * 60);
        assertThat(redisTemplate.getExpire("exam:timeout:" + vocabulary.getAttemptId())).isBetween(470L, 480L);
        assertThat(attemptRepository.findById(vocabulary.getAttemptId()).orElseThrow().getDurationSeconds())
                .isEqualTo(8 * 60);

        assertThatThrownBy(() -> jlptExamService.startNextSection(userId, sittingId))
                .isInstanceOf(BadRequestException.class);

        // Hết giờ phần Từ vựng (marker Redis hết hạn -> chốt TIMEOUT): chỉ chốt phần này, buổi thi vẫn đang làm.
        examService.saveAnswer(userId, vocabulary.getAttemptId(), answer(vocabulary.getQuestions().get(0).getId()));
        examFinalizationService.finalize(vocabulary.getAttemptId(), ExamAttemptStatus.TIMEOUT);

        ExamSittingResponse halfway = jlptExamService.getSitting(userId, sittingId);
        assertThat(halfway.getStatus()).isEqualTo(ExamSittingStatus.IN_PROGRESS);
        assertThat(halfway.getNextSection()).isEqualTo(ExamSection.GRAMMAR);
        assertThat(halfway.getSections())
                .extracting(ExamSittingResponse.Section::getName, ExamSittingResponse.Section::getStatus,
                        ExamSittingResponse.Section::getTotalScore, ExamSittingResponse.Section::getTotalQuestions)
                .containsExactly(tuple(ExamSection.VOCABULARY, ExamAttemptStatus.TIMEOUT, 1, 3),
                        tuple(ExamSection.GRAMMAR, null, null, null));
        // Phần của đề JLPT không lên bảng xếp hạng thi nhanh.
        assertThat(leaderboardService.getMyRank(LEVEL, userId).getScore()).isNull();

        // Câu ngữ pháp dính tới từ đã hỏi ở phần Từ vựng bị bỏ; đủ 2 câu nên đủ 5 phút.
        StartExamResponse grammar = jlptExamService.startNextSection(userId, sittingId);
        assertThat(grammar.getSection()).isEqualTo(ExamSection.GRAMMAR);
        assertThat(grammar.getSittingId()).isEqualTo(sittingId);
        assertThat(grammar.getQuestions()).extracting(ExamQuestionPublicResponse::getId)
                .containsExactlyInAnyOrder(grammarOnFourthWord.getId(), grammarOnFifthWord.getId())
                .doesNotContain(grammarOnFirstWord.getId());
        assertThat(grammar.getRemainingSeconds()).isEqualTo(5 * 60);
        assertThat(examService.getSession(userId, grammar.getAttemptId()).getSection()).isEqualTo(ExamSection.GRAMMAR);

        assertThat(examService.submit(userId, grammar.getAttemptId()).getSittingId()).isEqualTo(sittingId);

        ExamSittingResponse finished = jlptExamService.getSitting(userId, sittingId);
        assertThat(finished.getStatus()).isEqualTo(ExamSittingStatus.COMPLETED);
        assertThat(finished.getFinishedAt()).isNotNull();
        assertThat(finished.getNextSection()).isNull();
        assertThat(finished.getSections()).extracting(ExamSittingResponse.Section::getStatus)
                .containsExactly(ExamAttemptStatus.TIMEOUT, ExamAttemptStatus.COMPLETED);
        assertThatThrownBy(() -> jlptExamService.startNextSection(userId, sittingId))
                .isInstanceOf(BadRequestException.class);

        ExamReviewResponse review = examService.getReview(userId, vocabulary.getAttemptId());
        assertThat(review.getSittingId()).isEqualTo(sittingId);
        assertThat(review.getMondai())
                .extracting(ExamReviewResponse.MondaiScore::getNumber, ExamReviewResponse.MondaiScore::getType,
                        ExamReviewResponse.MondaiScore::getCorrect, ExamReviewResponse.MondaiScore::getTotal)
                .containsExactly(tuple(1, KANJI_READING, 1, 2), tuple(2, CONTEXT, 0, 1));
        // Câu xem lại theo đúng thứ tự trong bài.
        assertThat(review.getQuestions()).extracting(item -> item.getQuestionId())
                .containsExactlyElementsOf(vocabulary.getQuestions().stream().map(ExamQuestionPublicResponse::getId)
                        .toList());
    }

    @Test
    void aNewSitting_shouldFirstAskQuestionsTheLearnerHasNotMetYet() {
        StartExamResponse first = jlptExamService.startSitting(userId, request(ExamSection.GRAMMAR));
        examService.submit(userId, first.getAttemptId());
        List<Long> asked = first.getQuestions().stream().map(ExamQuestionPublicResponse::getId).toList();
        Long notAsked = Stream.of(grammarOnFirstWord, grammarOnFourthWord, grammarOnFifthWord).map(ExamQuestion::getId)
                .filter(id -> !asked.contains(id))
                .findFirst().orElseThrow();

        StartExamResponse second = jlptExamService.startSitting(userId, request(ExamSection.GRAMMAR));

        // 3 câu ngữ pháp, đề lấy 2: lần đầu gặp 2 câu, lần sau phải có câu còn lại.
        assertThat(asked).hasSize(2);
        assertThat(second.getQuestions()).extracting(ExamQuestionPublicResponse::getId).hasSize(2).contains(notAsked);
    }

    @Test
    void reconciliationJob_shouldTimeOutEachAttemptByItsOwnDuration() {
        LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);
        ExamSitting sitting = sittingRepository.save(ExamSitting.builder().userId(userId).jlptLevel(LEVEL)
                .sections(ExamSection.VOCABULARY).startedAt(twoMinutesAgo).build());
        UserExamAttempt oneMinuteSection = attemptRepository.save(UserExamAttempt.builder().userId(userId)
                .jlptLevel(LEVEL).sittingId(sitting.getId()).section(ExamSection.VOCABULARY).durationSeconds(60)
                .startedAt(twoMinutesAgo).build());
        UserExamAttempt quickExam = attemptRepository.save(UserExamAttempt.builder().userId(userId)
                .jlptLevel(LEVEL).startedAt(twoMinutesAgo).build());
        attemptIds.addAll(List.of(oneMinuteSection.getId(), quickExam.getId()));
        examSessionStore.initSession(oneMinuteSection.getId(), List.of(grammarOnFourthWord.getId()), 60);
        examSessionStore.initSession(quickExam.getId(), List.of(grammarOnFifthWord.getId()), 1800);

        reconciliationJob.reconcileExpiredAttempts();

        // Phần thi 1 phút đã quá giờ; bài thi nhanh 30 phút mới làm được 2 phút.
        assertThat(attemptRepository.findById(oneMinuteSection.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamAttemptStatus.TIMEOUT);
        assertThat(attemptRepository.findById(quickExam.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(sittingRepository.findById(sitting.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamSittingStatus.COMPLETED);
    }

    @Test
    void cleanupJob_shouldAbandonSittingsLeftHalfwayForTooLong_keepingTheirResults() {
        LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);
        ExamSitting halfway = sittingRepository.save(ExamSitting.builder().userId(userId).jlptLevel(LEVEL)
                .sections(ExamSection.VOCABULARY + "," + ExamSection.GRAMMAR).startedAt(fourHoursAgo).build());
        UserExamAttempt done = attemptRepository.save(UserExamAttempt.builder().userId(userId).jlptLevel(LEVEL)
                .sittingId(halfway.getId()).section(ExamSection.VOCABULARY).durationSeconds(600)
                .status(ExamAttemptStatus.COMPLETED).totalScore(3).startedAt(fourHoursAgo).build());
        ExamSitting recent = sittingRepository.save(ExamSitting.builder().userId(userId).jlptLevel(LEVEL)
                .sections(ExamSection.VOCABULARY + "," + ExamSection.GRAMMAR)
                .startedAt(LocalDateTime.now().minusMinutes(20)).build());

        sittingCleanupJob.closeStaleSittings();

        ExamSitting abandoned = sittingRepository.findById(halfway.getId()).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(ExamSittingStatus.ABANDONED);
        assertThat(abandoned.getFinishedAt()).isNotNull();
        assertThat(attemptRepository.findById(done.getId())).isPresent();
        assertThat(sittingRepository.findById(recent.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamSittingStatus.IN_PROGRESS);
    }

    private ExamQuestion question(String type, String skill, Long wordId) {
        return questionRepository.save(ExamQuestion.builder().jlptLevel(LEVEL).questionText(type)
                .optionA("1").optionB("2").optionC("3").optionD("4").correctOption("A")
                .skill(skill).questionType(type).kanjiIds(Set.of(wordId)).build());
    }

    private static JlptBlueprintProperties.Section section(String name, int minutes) {
        JlptBlueprintProperties.Section section = new JlptBlueprintProperties.Section();
        section.setName(name);
        section.setMinutes(minutes);
        section.setQuestions(new LinkedHashMap<>());
        return section;
    }

    private static StartJlptExamRequest request(String... sections) {
        StartJlptExamRequest request = new StartJlptExamRequest();
        request.setJlptLevel(LEVEL);
        request.setSections(List.of(sections));
        return request;
    }

    private static SaveAnswerRequest answer(Long questionId) {
        SaveAnswerRequest request = new SaveAnswerRequest();
        request.setQuestionId(questionId);
        request.setSelectedOption("A");
        return request;
    }
}
