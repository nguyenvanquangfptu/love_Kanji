package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.AdminExamPassageResponse;
import com.kanjimastery.backend.dto.ExamPassageResponse;
import com.kanjimastery.backend.dto.ExamQuestionPublicResponse;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Đoạn văn 文章の文法 trên PostgreSQL + Redis thật: duyệt cả đoạn một lần, đề lấy trọn đoạn theo thứ tự chỗ trống,
 * bài làm và trang xem lại có kèm đoạn văn.
 */
class ExamPassageIT extends AbstractIntegrationTest {

    /** Cấp độ giả với phần Ngữ pháp chỉ có 2 câu 文章の文法. */
    private static final JlptLevel LEVEL = JlptLevel.N1;
    private static final String CONTENT = "わたしは毎朝パンを食べます。【1】、きのうはご飯を食べました。"
            + "ご飯を食べて【2】、学校へ行きました。";

    @Autowired
    private ExamPassageReviewService passageReviewService;
    @Autowired
    private ExamQuestionReviewService questionReviewService;
    @Autowired
    private JlptExamService jlptExamService;
    @Autowired
    private ExamService examService;
    @Autowired
    private JlptBlueprintProperties blueprints;
    @Autowired
    private ExamPassageRepository passageRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private ExamSessionStore examSessionStore;
    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private ExamPassage passage;
    private ExamQuestion first;
    private ExamQuestion second;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("passage_" + suffix)
                .email("passage_" + suffix + "@test.local").passwordHash("x").build()).getId();
        passage = passageRepository.save(ExamPassage.builder().jlptLevel(LEVEL).title("わたしの朝ご飯")
                .content(CONTENT).build());
        // Lưu câu thứ hai trước: thứ tự trong đề phải theo chỗ trống, không theo id.
        second = blank(2, "から", "まで", "ので", "のに");
        first = blank(1, "でも", "だから", "それに", "そして");

        JlptBlueprintProperties.Section grammar = new JlptBlueprintProperties.Section();
        grammar.setName(ExamSection.GRAMMAR);
        grammar.setMinutes(5);
        grammar.setQuestions(new LinkedHashMap<>());
        grammar.getQuestions().put(JlptQuestionType.TEXT_GRAMMAR, 2);
        JlptBlueprintProperties.Level level = new JlptBlueprintProperties.Level();
        level.setSections(List.of(grammar));
        blueprints.getLevels().put(LEVEL, level);
    }

    @AfterEach
    void tearDown() {
        blueprints.getLevels().remove(LEVEL);
        attemptRepository.findAll().stream().filter(attempt -> attempt.getUserId().equals(userId))
                .forEach(attempt -> examSessionStore.cleanup(attempt.getId()));
        userRepository.deleteById(userId);
        // Xoá đoạn văn thì câu hỏi của nó đi theo (ON DELETE CASCADE).
        passageRepository.deleteById(passage.getId());
    }

    @Test
    void aPassage_shouldBeReviewedAsAWhole_andComeIntoTheExamWithAllItsBlanksInOrder() {
        AdminExamPassageResponse draft = passageReviewService.search(LEVEL.name(), ExamQuestionStatus.DRAFT, 0, 10)
                .getContent().get(0);
        assertThat(draft.getQuestions()).extracting(question -> question.getId())
                .containsExactly(first.getId(), second.getId());
        // Câu của đoạn không hiện riêng ở danh sách câu, và không duyệt lẻ được.
        assertThat(questionReviewService.search(new ExamQuestionReviewService.Filter(LEVEL.name(), null, null, false, null, false),
                0, 20).getContent()).isEmpty();
        assertThatThrownBy(() -> questionReviewService.changeStatus(first.getId(), ExamQuestionStatus.APPROVED, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> passageReviewService.update(passage.getId(), null, CONTENT.replace("【2】", "")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("【2】");

        AdminExamPassageResponse approved = passageReviewService.changeStatus(passage.getId(),
                ExamQuestionStatus.APPROVED, null);
        assertThat(approved.getQuestions()).extracting(question -> question.getStatus())
                .containsOnly(ExamQuestionStatus.APPROVED);

        StartExamResponse exam = jlptExamService.startSitting(userId, request());
        assertThat(exam.getQuestions())
                .extracting(ExamQuestionPublicResponse::getId, ExamQuestionPublicResponse::getPassageId,
                        ExamQuestionPublicResponse::getBlankNo)
                .containsExactly(tuple(first.getId(), passage.getId(), 1), tuple(second.getId(), passage.getId(), 2));
        assertThat(exam.getPassages()).extracting(ExamPassageResponse::content).containsExactly(CONTENT);

        examService.submit(userId, exam.getAttemptId());
        ExamReviewResponse review = examService.getReview(userId, exam.getAttemptId());
        assertThat(review.getPassages()).extracting(ExamPassageResponse::title).containsExactly("わたしの朝ご飯");
        assertThat(review.getQuestions()).extracting(QuestionReviewItem::getBlankNo).containsExactly(1, 2);

        // Lần làm đề sau: đoạn văn chưa gặp đứng trước đoạn đã làm.
        ExamPassage unseen = passageRepository.save(ExamPassage.builder().jlptLevel(LEVEL).content("【1】")
                .review(new ReviewState(ExamQuestionStatus.APPROVED)).build());
        try {
            assertThat(passageRepository.findApprovedForLearner(userId, LEVEL.name(), 10)).extracting(ExamPassage::getId)
                    .containsExactly(unseen.getId(), passage.getId());
        } finally {
            passageRepository.deleteById(unseen.getId());
        }
    }

    private ExamQuestion blank(int blankNo, String correct, String... others) {
        return questionRepository.save(ExamQuestion.builder().jlptLevel(LEVEL)
                .questionText("Chọn từ điền vào chỗ trống 【" + blankNo + "】.").optionA(correct).optionB(others[0])
                .optionC(others[1]).optionD(others[2]).correctOption("A")
                .questionType(JlptQuestionType.TEXT_GRAMMAR).review(new ReviewState(ExamQuestionStatus.DRAFT))
                .passageId(passage.getId()).blankNo(blankNo).build());
    }

    private static StartJlptExamRequest request() {
        StartJlptExamRequest request = new StartJlptExamRequest();
        request.setJlptLevel(LEVEL.name());
        request.setSections(List.of("GRAMMAR"));
        return request;
    }
}
