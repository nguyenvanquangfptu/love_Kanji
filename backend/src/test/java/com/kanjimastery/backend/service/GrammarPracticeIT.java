package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.PracticeQuestionResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.dto.WeakGrammarResponse;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Chẩn đoán ngữ pháp trên PostgreSQL thật: điểm ngữ pháp hay sai tính trên các lượt thi đã chốt gần đây (không tính câu
 * bỏ trống), câu luyện lại chỉ gồm câu đã duyệt, đứng riêng, đúng cấp độ.
 */
class GrammarPracticeIT extends AbstractIntegrationTest {

    /** Cấp độ giả, để không đụng tới dữ liệu N5-N3. */
    private static final JlptLevel LEVEL = JlptLevel.N1;

    @Autowired
    private GrammarPracticeService practiceService;
    @Autowired
    private GrammarPointRepository grammarPointRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private ExamPassageRepository passageRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private UserExamAnswerRepository answerRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private GrammarPoint because;
    private GrammarPoint after;
    private final List<Long> questionIds = new ArrayList<>();
    private ExamPassage passage;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("practice_" + suffix)
                .email("practice_" + suffix + "@test.local").passwordHash("x").build()).getId();
        because = grammarPointRepository.save(GrammarPoint.builder().jlptLevel(LEVEL).pattern("〜から（lý do）")
                .meaningVi("Vì ~ nên").build());
        after = grammarPointRepository.save(GrammarPoint.builder().jlptLevel(LEVEL).pattern("Vてから")
                .meaningVi("Sau khi V1 rồi V2").build());
        passage = passageRepository.save(ExamPassage.builder().jlptLevel(LEVEL).content("【1】")
                .review(new ReviewState(ExamQuestionStatus.APPROVED)).build());
    }

    @AfterEach
    void tearDown() {
        // Xoá người dùng thì lượt thi và câu trả lời đi theo (ON DELETE CASCADE).
        userRepository.deleteById(userId);
        questionRepository.deleteAllById(questionIds);
        passageRepository.deleteById(passage.getId());
        grammarPointRepository.deleteAllById(List.of(because.getId(), after.getId()));
    }

    @Test
    void weakPoints_shouldCountRecentScoredMistakes_mostFirst_ignoringBlanks() {
        ExamQuestion becauseOne = question(ExamQuestionStatus.APPROVED, because);
        ExamQuestion becauseTwo = question(ExamQuestionStatus.APPROVED, because);
        ExamQuestion becauseThree = question(ExamQuestionStatus.APPROVED, because);
        ExamQuestion afterOne = question(ExamQuestionStatus.APPROVED, after);
        ExamQuestion afterTwo = question(ExamQuestionStatus.APPROVED, after);

        // Đề gần đây: sai hai câu から, bỏ trống câu から thứ ba; Vてから đúng một sai một.
        Long recent = attempt(ExamAttemptStatus.COMPLETED, LocalDateTime.now().minusDays(1));
        answer(recent, becauseOne, "B", false);
        answer(recent, becauseTwo, "C", false);
        answer(recent, becauseThree, null, false);
        answer(recent, afterOne, "A", true);
        answer(recent, afterTwo, "D", false);
        // Đề quá 60 ngày và đề đang làm dở không tính.
        Long old = attempt(ExamAttemptStatus.COMPLETED, LocalDateTime.now().minusDays(90));
        answer(old, afterOne, "B", false);
        answer(old, afterTwo, "B", false);
        Long running = attempt(ExamAttemptStatus.IN_PROGRESS, LocalDateTime.now());
        answer(running, afterOne, "B", false);

        List<WeakGrammarResponse> weak = practiceService.weakPoints(userId, "n1");

        assertThat(weak).extracting(WeakGrammarResponse::id, WeakGrammarResponse::wrong, WeakGrammarResponse::answered)
                .containsExactly(tuple(because.getId(), 2, 2), tuple(after.getId(), 1, 2));
        assertThat(practiceService.weakPoints(userId, "N2")).isEmpty();
    }

    @Test
    void practice_shouldOnlyUseApprovedStandaloneQuestionsOfTheLevel_forTheChosenPoints() {
        ExamQuestion approved = question(ExamQuestionStatus.APPROVED, because, after);
        question(ExamQuestionStatus.DRAFT, because);
        ExamQuestion blank = question(ExamQuestionStatus.APPROVED, because);
        blank.setPassageId(passage.getId());
        blank.setBlankNo(1);
        questionRepository.save(blank);
        question(ExamQuestionStatus.APPROVED, after);

        List<PracticeQuestionResponse> practice = practiceService.practice("N1", List.of(because.getId()));

        assertThat(practice).singleElement().satisfies(question -> {
            assertThat(question.id()).isEqualTo(approved.getId());
            assertThat(question.correctOption()).isEqualTo("A");
            assertThat(question.grammarPoints()).extracting(QuestionReviewItem.Grammar::pattern)
                    .containsExactlyInAnyOrder("〜から（lý do）", "Vてから");
        });
    }

    private ExamQuestion question(ExamQuestionStatus status, GrammarPoint... points) {
        Set<Long> pointIds = new HashSet<>();
        for (GrammarPoint point : points) {
            pointIds.add(point.getId());
        }
        ExamQuestion question = questionRepository.save(ExamQuestion.builder().jlptLevel(LEVEL)
                .questionText("[PracticeIT] Chọn").sentence("雨が降った（　　）、家にいました。").optionA("から")
                .optionB("のに").optionC("まで").optionD("ても").correctOption("A")
                .questionType(JlptQuestionType.GRAMMAR_FORM).review(new ReviewState(status)).grammarPointIds(pointIds).build());
        questionIds.add(question.getId());
        return question;
    }

    private Long attempt(ExamAttemptStatus status, LocalDateTime startedAt) {
        return attemptRepository.save(UserExamAttempt.builder().userId(userId).jlptLevel(LEVEL).status(status)
                .startedAt(startedAt).build()).getId();
    }

    private void answer(Long attemptId, ExamQuestion question, String selected, boolean correct) {
        answerRepository.save(UserExamAnswer.builder().attemptId(attemptId).questionId(question.getId())
                .selectedOption(selected).isCorrect(correct).build());
    }
}
