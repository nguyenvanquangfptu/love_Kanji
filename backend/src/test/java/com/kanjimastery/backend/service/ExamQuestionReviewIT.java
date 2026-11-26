package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.AdminExamQuestionRequest;
import com.kanjimastery.backend.dto.AdminExamQuestionResponse;
import com.kanjimastery.backend.dto.QuestionBankStatsResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** Duyệt câu thi trên PostgreSQL thật: câu nháp không vào đề cho tới khi được duyệt; loại phải có lý do. */
class ExamQuestionReviewIT extends AbstractIntegrationTest {

    @Autowired
    private ExamQuestionReviewService reviewService;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private GrammarPointRepository grammarPointRepository;
    @Autowired
    private ExamPassageRepository passageRepository;

    private GrammarPoint particle;
    private ExamQuestion draft;

    @BeforeEach
    void setUp() {
        particle = grammarPointRepository.save(GrammarPoint.builder().jlptLevel(JlptLevel.N5).lesson("N5-06").pattern("NをV")
                .meaningVi("Làm V (tác động lên N)").build());
        draft = questionRepository.save(ExamQuestion.builder().jlptLevel(JlptLevel.N5).questionText("[ReviewIT] Chọn trợ từ")
                .sentence("パン（　　）食べます。").optionA("が").optionB("を").optionC("に").optionD("で")
                .correctOption("B").questionType(JlptQuestionType.GRAMMAR_FORM)
                .review(new ReviewState(ExamQuestionStatus.DRAFT, ExamQuestionFlag.AMBIGUOUS,
                        "Máy giải lại: A cũng có thể đúng", null))
                .grammarPointIds(new HashSet<>(Set.of(particle.getId()))).build());
    }

    @AfterEach
    void tearDown() {
        questionRepository.deleteById(draft.getId());
        grammarPointRepository.deleteById(particle.getId());
    }

    @Test
    void aDraft_shouldStayOutOfExamsUntilApproved_andRejectingNeedsAReason() {
        ExamQuestionReviewService.Filter flaggedDrafts = new ExamQuestionReviewService.Filter("n5",
                JlptQuestionType.GRAMMAR_FORM, ExamQuestionStatus.DRAFT, true, particle.getId(), false);
        assertThat(reviewService.search(flaggedDrafts, 0, 20).getContent()).singleElement().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(draft.getId());
            assertThat(found.getGrammarPoints()).extracting(AdminExamQuestionResponse.Grammar::pattern)
                    .containsExactly("NをV");
        });
        assertThat(examPool()).doesNotContain(draft.getId());

        assertThatThrownBy(() -> reviewService.changeStatus(draft.getId(), ExamQuestionStatus.REJECTED, " "))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reviewService.update(draft.getId(), request("が", "が")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("có lựa chọn trùng nhau");

        AdminExamQuestionResponse edited = reviewService.update(draft.getId(), request("は", "を"));
        assertThat(edited.getOptionA()).isEqualTo("は");
        assertThat(edited.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);

        AdminExamQuestionResponse approved = reviewService.changeStatus(draft.getId(), ExamQuestionStatus.APPROVED, null);
        // Người duyệt đã xem cảnh báo của máy: duyệt rồi thì bỏ cờ.
        assertThat(approved.getFlag()).isNull();
        assertThat(approved.getReviewedAt()).isNotNull();
        assertThat(examPool()).contains(draft.getId());
        QuestionBankStatsResponse n5 = reviewService.stats().stream()
                .filter(stats -> stats.jlptLevel() == JlptLevel.N5).findFirst().orElseThrow();
        assertThat(n5.types()).filteredOn(type -> type.type().equals(JlptQuestionType.GRAMMAR_FORM)).singleElement()
                .satisfies(type -> {
                    assertThat(type.approved()).isGreaterThanOrEqualTo(1);
                    assertThat(type.perExam()).isEqualTo(9);
                });

        AdminExamQuestionResponse retired = reviewService.changeStatus(draft.getId(), ExamQuestionStatus.RETIRED,
                "Báo sai: hai đáp án cùng đúng");
        assertThat(retired.getReviewNote()).isEqualTo("Báo sai: hai đáp án cùng đúng");
        assertThat(examPool()).doesNotContain(draft.getId());
    }

    @Test
    void approveAll_shouldApproveOnlyCleanDrafts_andSayWhyTheOthersStayed() {
        ExamQuestion clean = questionRepository.save(grammarQuestion("を", "が").build());
        ExamQuestion approved = questionRepository.save(grammarQuestion("を", "が")
                .review(new ReviewState(ExamQuestionStatus.APPROVED)).build());
        ExamQuestion broken = questionRepository.save(grammarQuestion("を", "を").build());
        ExamPassage passage = passageRepository.save(ExamPassage.builder().jlptLevel(JlptLevel.N5).section(ExamSection.GRAMMAR).content("【1】").build());
        ExamQuestion blank = questionRepository.save(grammarQuestion("を", "が").questionText("【1】")
                .questionType(JlptQuestionType.TEXT_GRAMMAR).passageId(passage.getId()).blankNo(1).build());
        try {
            ExamQuestionReviewService.BulkApproval result = reviewService.approveAll(List.of(clean.getId(),
                    draft.getId(), approved.getId(), broken.getId(), blank.getId(), -1L, clean.getId()));

            assertThat(result.approved()).isEqualTo(1);
            assertThat(result.skipped()).extracting(ExamQuestionReviewService.BulkApproval.Skipped::id)
                    .containsExactly(draft.getId(), approved.getId(), broken.getId(), blank.getId(), -1L);
            assertThat(result.skipped()).extracting(ExamQuestionReviewService.BulkApproval.Skipped::reason)
                    .satisfiesExactly(
                            reason -> assertThat(reason).contains("cảnh báo"),
                            reason -> assertThat(reason).contains("không ở trạng thái chờ duyệt"),
                            reason -> assertThat(reason).contains("có lựa chọn trùng nhau"),
                            reason -> assertThat(reason).contains("đoạn văn"),
                            reason -> assertThat(reason).contains("không tìm thấy"));
            assertThat(questionRepository.findById(clean.getId()).orElseThrow()).satisfies(question -> {
                assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.APPROVED);
                assertThat(question.getReviewedAt()).isNotNull();
            });
            // Câu có cảnh báo vẫn chờ người duyệt xem riêng.
            assertThat(questionRepository.findById(draft.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.DRAFT);
            assertThat(questionRepository.findById(broken.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.DRAFT);
        } finally {
            questionRepository.deleteAllById(List.of(clean.getId(), approved.getId(), broken.getId()));
            passageRepository.deleteById(passage.getId());
        }
    }

    @Test
    void pendingDrafts_shouldBeCountedBySourceAndTest_andSampledAtRandom() {
        List<ExamQuestion> saved = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            saved.add(questionRepository.save(grammarQuestion("を", "が").source(ExamQuestionSource.IMPORTED)
                    .sourceRef("IT-SAMPLE/NP/" + i).build()));
        }
        saved.add(questionRepository.save(grammarQuestion("を", "が").source(ExamQuestionSource.IMPORTED)
                .sourceRef("IT-SAMPLE/NP/13").review(new ReviewState(ExamQuestionStatus.APPROVED)).build()));
        Set<Long> drafts = saved.subList(0, 12).stream().map(ExamQuestion::getId).collect(Collectors.toSet());
        ExamQuestionReviewService.Filter test = new ExamQuestionReviewService.Filter("N5", null, null, false, null, false,
                "IT-SAMPLE");
        try {
            ExamQuestionReviewService.PendingSummary summary = reviewService.pendingSummary(test);
            assertThat(summary.total()).as("câu đã duyệt không tính").isEqualTo(12);
            assertThat(summary.bySource()).containsExactly(entry(ExamQuestionSource.IMPORTED, 12L));
            assertThat(summary.byTest()).containsExactly(new ExamQuestionReviewService.PendingSummary.TestCount("IT-SAMPLE", 12));

            // 10% của 12 câu là 2, nhưng một lượt duyệt mẫu có ít nhất 10 câu.
            List<AdminExamQuestionResponse> sample = reviewService.sample(test, 10, new Random(7));
            assertThat(sample).hasSize(10).extracting(AdminExamQuestionResponse::getId).doesNotHaveDuplicates()
                    .allMatch(drafts::contains);
            assertThat(reviewService.sample(test, 100, new Random(7))).hasSize(12);
        } finally {
            questionRepository.deleteAll(saved);
        }
    }

    private static ExamQuestion.ExamQuestionBuilder grammarQuestion(String optionA, String optionB) {
        return ExamQuestion.builder().jlptLevel(JlptLevel.N5).questionText("[ReviewIT] Chọn trợ từ").sentence("パン（　　）食べます。")
                .optionA(optionA).optionB(optionB).optionC("に").optionD("で").correctOption("A")
                .questionType(JlptQuestionType.GRAMMAR_FORM).review(new ReviewState(ExamQuestionStatus.DRAFT));
    }

    private List<Long> examPool() {
        return questionRepository.findForLearnerByLevelAndType(0L, "N5", JlptQuestionType.GRAMMAR_FORM.name(), null, 1000).stream()
                .map(ExamQuestion::getId).toList();
    }

    private static AdminExamQuestionRequest request(String optionA, String optionB) {
        AdminExamQuestionRequest request = new AdminExamQuestionRequest();
        request.setQuestionText("[ReviewIT] Chọn trợ từ");
        request.setSentence("パン（　　）食べます。");
        request.setOptionA(optionA);
        request.setOptionB(optionB);
        request.setOptionC("に");
        request.setOptionD("で");
        request.setCorrectOption("B");
        request.setExplanation("NをV: tân ngữ của động từ.");
        return request;
    }
}
