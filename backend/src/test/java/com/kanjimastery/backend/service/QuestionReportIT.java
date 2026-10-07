package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.AdminExamQuestionResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.QuestionReportReason;
import com.kanjimastery.backend.model.QuestionReportStatus;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionReportRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Báo lỗi câu hỏi trên PostgreSQL thật: chỉ báo được câu đã làm, đủ người báo thì câu tự rút khỏi đề (câu của đoạn văn
 * thì rút cả đoạn), người duyệt xử lý hoặc bỏ qua thì báo lỗi được đóng.
 */
class QuestionReportIT extends AbstractIntegrationTest {

    private static final JlptLevel LEVEL = JlptLevel.N1;

    @Autowired
    private QuestionReportService reportService;
    @Autowired
    private ExamQuestionReviewService questionReviewService;
    @Autowired
    private ExamPassageReviewService passageReviewService;
    @Autowired
    private ExamService examService;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private ExamPassageRepository passageRepository;
    @Autowired
    private ExamQuestionReportRepository reportRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private UserExamAnswerRepository answerRepository;
    @Autowired
    private UserRepository userRepository;

    private final List<Long> learners = new ArrayList<>();
    private final List<Long> attempts = new ArrayList<>();
    /** Người dùng chưa làm bài nào. */
    private Long stranger;
    private ExamQuestion standalone;
    private ExamPassage passage;
    private ExamQuestion firstBlank;
    private ExamQuestion secondBlank;

    @BeforeEach
    void setUp() {
        standalone = questionRepository.save(question().build());
        passage = passageRepository.save(ExamPassage.builder().jlptLevel(LEVEL).content("【1】【2】")
                .status(ExamQuestionStatus.APPROVED).build());
        firstBlank = questionRepository.save(question().questionText("【1】").questionType(JlptQuestionType.TEXT_GRAMMAR)
                .sentence(null).passageId(passage.getId()).blankNo(1).build());
        secondBlank = questionRepository.save(question().questionText("【2】").questionType(JlptQuestionType.TEXT_GRAMMAR)
                .sentence(null).passageId(passage.getId()).blankNo(2).build());
        for (int i = 0; i < 3; i++) {
            Long userId = user("report_" + i);
            learners.add(userId);
            Long attemptId = attemptRepository.save(UserExamAttempt.builder().userId(userId).jlptLevel(LEVEL)
                    .status(ExamAttemptStatus.COMPLETED).startedAt(LocalDateTime.now().minusMinutes(5)).build()).getId();
            attempts.add(attemptId);
            for (ExamQuestion question : List.of(standalone, firstBlank, secondBlank)) {
                answerRepository.save(UserExamAnswer.builder().attemptId(attemptId).questionId(question.getId())
                        .selectedOption("B").isCorrect(false).build());
            }
        }
        stranger = user("report_stranger");
    }

    @AfterEach
    void tearDown() {
        // Xoá người dùng thì lượt thi, câu trả lời và báo lỗi của họ đi theo (ON DELETE CASCADE).
        userRepository.deleteAllById(learners);
        userRepository.deleteById(stranger);
        questionRepository.deleteById(standalone.getId());
        passageRepository.deleteById(passage.getId());
    }

    @Test
    void report_shouldNeedAFinishedAnswer_andWithdrawTheQuestionOnceThreeLearnersReportIt() {
        assertThatThrownBy(() -> reportService.report(stranger, standalone.getId(), QuestionReportReason.WRONG_ANSWER,
                null)).isInstanceOf(BadRequestException.class);

        // Báo lại thì cập nhật lý do, không tính thêm một người.
        reportService.report(learners.get(0), standalone.getId(), QuestionReportReason.WRONG_ANSWER, null);
        reportService.report(learners.get(0), standalone.getId(), QuestionReportReason.AMBIGUOUS, "  B cũng đúng  ");
        reportService.report(learners.get(1), standalone.getId(), QuestionReportReason.AMBIGUOUS, null);
        assertThat(status(standalone)).isEqualTo(ExamQuestionStatus.APPROVED);

        reportService.report(learners.get(2), standalone.getId(), QuestionReportReason.WRONG_ANSWER, null);

        ExamQuestion withdrawn = questionRepository.findById(standalone.getId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(withdrawn.getFlag()).isEqualTo(ExamQuestionFlag.REPORTED);
        assertThat(withdrawn.getReviewNote()).contains("3 người học báo lỗi");
        AdminExamQuestionResponse reported = questionReviewService.search(
                        new ExamQuestionReviewService.Filter(LEVEL.name(), null, null, false, null, true), 0, 20)
                .getContent().stream().filter(found -> found.getId().equals(standalone.getId())).findFirst()
                .orElseThrow();
        assertThat(reported.getReports()).extracting(AdminExamQuestionResponse.Report::reason)
                .containsExactly(QuestionReportReason.AMBIGUOUS, QuestionReportReason.AMBIGUOUS,
                        QuestionReportReason.WRONG_ANSWER);
        assertThat(reported.getReports().get(0).note()).isEqualTo("B cũng đúng");
        // Trang xem lại cho biết người học đã báo lỗi câu nào.
        assertThat(examService.getReview(learners.get(0), attempts.get(0)).getQuestions())
                .filteredOn(QuestionReviewItem::isReported).extracting(QuestionReviewItem::getQuestionId)
                .containsExactly(standalone.getId());

        // Người duyệt sửa xong duyệt lại: báo lỗi được đóng, câu không còn trong danh sách bị báo lỗi.
        questionReviewService.changeStatus(standalone.getId(), ExamQuestionStatus.APPROVED, null);
        assertThat(reportRepository.countByQuestionIdAndStatus(standalone.getId(), QuestionReportStatus.OPEN)).isZero();
        assertThat(reportRepository.countByQuestionIdAndStatus(standalone.getId(), QuestionReportStatus.RESOLVED))
                .isEqualTo(3);
        assertThat(questionReviewService.search(new ExamQuestionReviewService.Filter(LEVEL.name(), null, null, false, null,
                true), 0, 20).getContent()).isEmpty();
    }

    @Test
    void reportsOnAPassageBlank_shouldWithdrawTheWholePassage_untilItIsReviewedAgain() {
        for (Long learner : learners) {
            reportService.report(learner, firstBlank.getId(), QuestionReportReason.AMBIGUOUS, null);
        }

        ExamPassage withdrawn = passageRepository.findById(passage.getId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(withdrawn.getFlag()).isEqualTo(ExamQuestionFlag.REPORTED);
        assertThat(status(firstBlank)).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(status(secondBlank)).isEqualTo(ExamQuestionStatus.DRAFT);

        passageReviewService.changeStatus(passage.getId(), ExamQuestionStatus.APPROVED, null);
        assertThat(status(secondBlank)).isEqualTo(ExamQuestionStatus.APPROVED);
        assertThat(reportRepository.countByQuestionIdAndStatus(firstBlank.getId(), QuestionReportStatus.OPEN)).isZero();
    }

    @Test
    void dismissReports_shouldCloseOpenReports_andClearTheReportFlag_withoutApprovingTheQuestion() {
        for (Long learner : learners) {
            reportService.report(learner, standalone.getId(), QuestionReportReason.UNCLEAR, null);
        }

        AdminExamQuestionResponse dismissed = questionReviewService.dismissReports(standalone.getId());

        assertThat(dismissed.getFlag()).isNull();
        assertThat(dismissed.getReports()).isEmpty();
        assertThat(dismissed.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
        assertThat(reportRepository.countByQuestionIdAndStatus(standalone.getId(), QuestionReportStatus.DISMISSED))
                .isEqualTo(3);
    }

    private ExamQuestionStatus status(ExamQuestion question) {
        return questionRepository.findById(question.getId()).orElseThrow().getStatus();
    }

    private Long user(String name) {
        String suffix = String.valueOf(System.nanoTime());
        return userRepository.save(User.builder().username(name + "_" + suffix).email(name + "_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
    }

    private static ExamQuestion.ExamQuestionBuilder question() {
        return ExamQuestion.builder().jlptLevel(LEVEL).questionText("[ReportIT] Chọn").sentence("駅（　　）行きます。")
                .optionA("へ").optionB("を").optionC("が").optionD("の").correctOption("A")
                .questionType(JlptQuestionType.GRAMMAR_FORM).status(ExamQuestionStatus.APPROVED);
    }
}
