package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.GrammarCardResponse;
import com.kanjimastery.backend.dto.GrammarReviewRequest;
import com.kanjimastery.backend.dto.GrammarReviewResponse;
import com.kanjimastery.backend.dto.GrammarSessionResponse;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.GrammarReviewLog;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.GrammarReviewLogRepository;
import com.kanjimastery.backend.repository.UserGrammarSrsRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ôn ngữ pháp theo lịch trên PostgreSQL thật (V38): thêm điểm an toàn khi bấm lại hay bấm cùng lúc, chọn câu hỏi (chỉ
 * câu đã duyệt, tránh câu vừa hỏi), giới hạn điểm mới theo ngày học bắt đầu 4 giờ sáng, và xoá câu hỏi / điểm ngữ
 * pháp không làm hỏng lịch sử ôn.
 */
class GrammarSrsIT extends AbstractIntegrationTest {

    @Autowired
    private GrammarSrsService grammarSrsService;
    @Autowired
    private StudyCalendar calendar;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GrammarPointRepository grammarPointRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private UserGrammarSrsRepository srsRepository;
    @Autowired
    private GrammarReviewLogRepository logRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long withQuestions;
    private Long withoutQuestions;
    private Long firstQuestion;
    private Long secondQuestion;
    private Long draftQuestion;
    private final List<Long> questionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("grammar_" + suffix)
                .email("grammar_" + suffix + "@test.local").passwordHash("x").build()).getId();
        withQuestions = point("〜てから" + suffix);
        withoutQuestions = point("〜ながら" + suffix);
        firstQuestion = question(withQuestions, ExamQuestionStatus.APPROVED);
        secondQuestion = question(withQuestions, ExamQuestionStatus.APPROVED);
        draftQuestion = question(withQuestions, ExamQuestionStatus.DRAFT);
    }

    @AfterEach
    void tearDown() {
        // Xoá người dùng thì thẻ và lịch sử ôn của họ đi theo (ON DELETE CASCADE).
        userRepository.deleteById(userId);
        questionRepository.deleteAllById(questionIds);
        grammarPointRepository.deleteAllById(List.of(withQuestions, withoutQuestions));
    }

    @Test
    void addCards_shouldAddEachPointOnce_whenTappedAgainOrTwiceAtTheSameTime() throws Exception {
        List<Long> ids = List.of(withQuestions, withoutQuestions, -1L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AddSrsCardsResponse>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return grammarSrsService.addCards(userId, ids);
            }));
        }
        start.countDown();
        int added = 0;
        for (Future<AddSrsCardsResponse> result : results) {
            added += result.get(10, TimeUnit.SECONDS).getAdded();
        }
        pool.shutdown();

        AddSrsCardsResponse again = grammarSrsService.addCards(userId, ids);

        // Id không tồn tại bị bỏ qua; hai lần bấm cùng lúc vẫn chỉ thêm mỗi điểm một lần.
        assertThat(added).isEqualTo(2);
        assertThat(again.getAdded()).isZero();
        assertThat(again.getAlreadyInReview()).isEqualTo(2);
        assertThat(srsRepository.findAllByUserIdAndGrammarPointIdIn(userId, List.of(withQuestions, withoutQuestions)))
                .hasSize(2);
    }

    @Test
    void session_shouldAskAnApprovedQuestion_andGradeItOnTheServer() {
        grammarSrsService.addCards(userId, List.of(withQuestions, withoutQuestions));

        GrammarSessionResponse session = grammarSrsService.session(userId);

        assertThat(session.cards()).extracting(GrammarCardResponse::grammarPointId)
                .containsExactly(withQuestions, withoutQuestions);
        GrammarCardResponse asked = session.cards().get(0);
        assertThat(asked.question().id()).isIn(firstQuestion, secondQuestion);
        assertThat(session.cards().get(1).question()).isNull();

        GrammarReviewRequest answer = new GrammarReviewRequest();
        answer.setGrammarPointId(withQuestions);
        answer.setQuestionId(asked.question().id());
        answer.setChosenOption("B");
        GrammarReviewResponse result = grammarSrsService.review(userId, answer);

        assertThat(result.correct()).isTrue();
        assertThat(result.nextReviewAt()).isEqualTo(calendar.startOf(calendar.today().plusDays(1)));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM grammar_review_logs
                WHERE user_id = ? AND question_id = ? AND chosen_option = 'B' AND rating = 3 AND state_before = 'NEW'
                """, Long.class, userId, asked.question().id())).isEqualTo(1L);
        // Đã ôn xong và chưa đến hạn: phiên còn lại chỉ điểm chưa học.
        assertThat(grammarSrsService.session(userId).cards()).extracting(GrammarCardResponse::grammarPointId)
                .containsExactly(withoutQuestions);
    }

    @Test
    void pickForGrammarReview_shouldAvoidTheQuestionAskedLastTime_andNeverPickADraft() {
        grammarSrsService.addCards(userId, List.of(withQuestions));
        logAt(withQuestions, firstQuestion, CardState.NEW, calendar.now().minusDays(1));

        for (int i = 0; i < 10; i++) {
            assertThat(questionRepository.pickForGrammarReview(userId, List.of(withQuestions, withoutQuestions)))
                    .singleElement()
                    .satisfies(pick -> {
                        assertThat(pick.getGrammarPointId()).isEqualTo(withQuestions);
                        assertThat(pick.getQuestionId()).isEqualTo(secondQuestion).isNotEqualTo(draftQuestion);
                    });
        }
    }

    @Test
    void session_shouldCountNewPointsPerStudyDay_startingAt4am() {
        grammarSrsService.addCards(userId, List.of(withQuestions, withoutQuestions));
        LocalDateTime startOfToday = calendar.startOfToday();
        // 3:59 sáng là ngày học hôm qua: không tính vào hôm nay.
        logAt(withoutQuestions, null, CardState.NEW, startOfToday.minusMinutes(1));
        int defaultNewPerDay = grammarSrsService.session(userId).newLeftToday();

        logAt(withoutQuestions, null, CardState.NEW, startOfToday.plusMinutes(1));

        assertThat(grammarSrsService.session(userId).newLeftToday()).isEqualTo(defaultNewPerDay - 1);
    }

    @Test
    void deletingAQuestion_shouldKeepTheReview_andDeletingAPoint_shouldRemoveItsCardsAndReviews() {
        grammarSrsService.addCards(userId, List.of(withQuestions));
        Long logId = logAt(withQuestions, firstQuestion, CardState.NEW, calendar.now()).getId();

        questionRepository.deleteById(firstQuestion);
        questionIds.remove(firstQuestion);
        assertThat(logRepository.findById(logId)).get().extracting(GrammarReviewLog::getQuestionId).isNull();

        questionRepository.deleteAllById(questionIds);
        questionIds.clear();
        grammarPointRepository.deleteById(withQuestions);
        assertThat(logRepository.findById(logId)).isEmpty();
        assertThat(srsRepository.findByUserIdAndGrammarPointId(userId, withQuestions)).isEmpty();
    }

    private Long point(String pattern) {
        return grammarPointRepository.save(GrammarPoint.builder().jlptLevel(JlptLevel.N5).lesson("N5-01")
                .pattern(pattern).meaningVi("IT").build()).getId();
    }

    private Long question(Long grammarPointId, ExamQuestionStatus status) {
        Long id = questionRepository.save(ExamQuestion.builder().jlptLevel(JlptLevel.N5)
                .questionType(JlptQuestionType.GRAMMAR_FORM).questionText("[GrammarSrsIT]")
                .sentence("ご飯を食べて（　　）、出かけます。").optionA("まで").optionB("から").optionC("ので")
                .optionD("のに").correctOption("B").review(new ReviewState(status))
                .grammarPointIds(Set.of(grammarPointId)).build()).getId();
        questionIds.add(id);
        return id;
    }

    private GrammarReviewLog logAt(Long grammarPointId, Long questionId, CardState stateBefore, LocalDateTime at) {
        return logRepository.save(GrammarReviewLog.builder().userId(userId).grammarPointId(grammarPointId)
                .questionId(questionId).chosenOption(questionId == null ? null : "A").correct(false)
                .rating(ReviewRating.AGAIN).stateBefore(stateBefore).reviewedAt(at).build());
    }
}
