package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.AdminExamQuestionResponse;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionStats;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamQuestionStatsRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Phân tích câu hỏi trên PostgreSQL thật: 40 lượt thi với độ giỏi tăng dần (10 câu nền, lượt thứ i đúng i/4 + 1
 * câu). Câu mà người giỏi đúng có độ phân biệt dương; câu mà người giỏi lại sai bị gắn cờ; câu đã duyệt lại sau các
 * lượt đó thì không tính các lượt cũ.
 */
class ItemAnalysisIT extends AbstractIntegrationTest {

    private static final String LEVEL = "N8";
    private static final int ATTEMPTS = 40;

    @Autowired
    private ItemAnalysisService analysisService;
    @Autowired
    private ExamQuestionReviewService reviewService;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private ExamQuestionStatsRepository statsRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private UserExamAnswerRepository answerRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private final List<ExamQuestion> base = new ArrayList<>();
    private ExamQuestion discriminating;
    private ExamQuestion backwards;
    private ExamQuestion reviewedAfterwards;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("analysis_" + suffix)
                .email("analysis_" + suffix + "@test.local").passwordHash("x").build()).getId();
        for (int i = 0; i < 10; i++) {
            base.add(questionRepository.save(question().build()));
        }
        discriminating = questionRepository.save(question().build());
        backwards = questionRepository.save(question().build());
        reviewedAfterwards = questionRepository.save(question().build());

        LocalDateTime anHourAgo = LocalDateTime.now().minusHours(1);
        for (int i = 0; i < ATTEMPTS; i++) {
            Long attemptId = attemptRepository.save(UserExamAttempt.builder().userId(userId).jlptLevel(LEVEL)
                    .status(ExamAttemptStatus.COMPLETED).startedAt(anHourAgo).build()).getId();
            int ability = i / 4;
            for (int b = 0; b < base.size(); b++) {
                answer(attemptId, base.get(b), b <= ability, anHourAgo);
            }
            answer(attemptId, discriminating, i >= ATTEMPTS / 2, anHourAgo);
            answer(attemptId, backwards, i < ATTEMPTS / 2, anHourAgo);
            answer(attemptId, reviewedAfterwards, i < ATTEMPTS / 2, anHourAgo);
        }
        // Người duyệt đã xem lại câu này sau các lượt thi trên: lượt cũ không còn tính.
        reviewedAfterwards.setReviewedAt(LocalDateTime.now());
        questionRepository.save(reviewedAfterwards);
    }

    @AfterEach
    void tearDown() {
        // Xoá người dùng thì lượt thi, câu trả lời đi theo; xoá câu thì thống kê đi theo (ON DELETE CASCADE).
        userRepository.deleteById(userId);
        questionRepository.deleteAll(questionRepository.findAll().stream()
                .filter(question -> LEVEL.equals(question.getJlptLevel())).toList());
    }

    @Test
    void analyze_shouldStoreStatsPerQuestion_andFlagApprovedQuestionsTheStrongLearnersMiss() {
        ItemAnalysisService.Summary summary = analysisService.analyze();

        assertThat(summary.flagged()).isEqualTo(1);
        ExamQuestionStats good = statsRepository.findById(discriminating.getId()).orElseThrow();
        assertThat(good.getResponses()).isEqualTo(ATTEMPTS);
        assertThat(good.getCorrectRate()).isCloseTo(0.5, within(0.001));
        assertThat(good.getDiscrimination()).isGreaterThan(0.5);
        ExamQuestionStats bad = statsRepository.findById(backwards.getId()).orElseThrow();
        assertThat(bad.getDiscrimination()).isLessThan(-0.5);
        assertThat(statsRepository.findById(reviewedAfterwards.getId())).isEmpty();

        ExamQuestion flagged = questionRepository.findById(backwards.getId()).orElseThrow();
        assertThat(flagged.getFlag()).isEqualTo(ExamQuestionFlag.STATS);
        assertThat(flagged.getStatus()).isEqualTo(ExamQuestionStatus.APPROVED);
        assertThat(flagged.getReviewNote()).contains("40 lượt").contains("người làm tốt các câu khác lại hay sai");
        assertThat(questionRepository.findById(discriminating.getId()).orElseThrow().getFlag()).isNull();

        // Trang duyệt hiện thống kê; chạy lại không gắn thêm cờ cho câu đang có cờ.
        AdminExamQuestionResponse shown = reviewService.search(new ExamQuestionReviewService.Filter(LEVEL, null, null,
                        true, null, false), 0, 20).getContent().stream()
                .filter(question -> question.getId().equals(backwards.getId())).findFirst().orElseThrow();
        assertThat(shown.getStats().responses()).isEqualTo(ATTEMPTS);
        assertThat(analysisService.analyze().flagged()).isZero();
    }

    @Test
    void suspicious_shouldNeedEnoughResponses_andANegativeOrFlatDiscrimination() {
        assertThat(ItemAnalysisService.suspicious(29, 0.5, -0.4)).isFalse();
        assertThat(ItemAnalysisService.suspicious(30, 0.5, -0.4)).isTrue();
        assertThat(ItemAnalysisService.suspicious(30, 0.1, 0.05)).isTrue();
        assertThat(ItemAnalysisService.suspicious(30, 0.1, 0.3)).isFalse();
        assertThat(ItemAnalysisService.suspicious(30, 0.6, 0.0)).isFalse();
        assertThat(ItemAnalysisService.suspicious(100, 0.5, null)).isFalse();
    }

    private void answer(Long attemptId, ExamQuestion question, boolean correct, LocalDateTime at) {
        answerRepository.save(UserExamAnswer.builder().attemptId(attemptId).questionId(question.getId())
                .selectedOption(correct ? "A" : "B").isCorrect(correct).answeredAt(at).build());
    }

    private static ExamQuestion.ExamQuestionBuilder question() {
        return ExamQuestion.builder().jlptLevel(LEVEL).questionText("[AnalysisIT] Chọn").sentence("駅（　　）行きます。")
                .optionA("へ").optionB("を").optionC("が").optionD("の").correctOption("A")
                .questionType(JlptQuestionType.GRAMMAR_FORM).status(ExamQuestionStatus.APPROVED);
    }
}
