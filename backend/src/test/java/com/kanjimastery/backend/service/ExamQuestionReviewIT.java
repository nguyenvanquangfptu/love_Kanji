package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.AdminExamQuestionRequest;
import com.kanjimastery.backend.dto.AdminExamQuestionResponse;
import com.kanjimastery.backend.dto.QuestionBankStatsResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Duyệt câu thi trên PostgreSQL thật: câu nháp không vào đề cho tới khi được duyệt; loại phải có lý do. */
class ExamQuestionReviewIT extends AbstractIntegrationTest {

    @Autowired
    private ExamQuestionReviewService reviewService;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private GrammarPointRepository grammarPointRepository;

    private GrammarPoint particle;
    private ExamQuestion draft;

    @BeforeEach
    void setUp() {
        particle = grammarPointRepository.save(GrammarPoint.builder().jlptLevel("N5").lesson("N5-06").pattern("NをV")
                .meaningVi("Làm V (tác động lên N)").build());
        draft = questionRepository.save(ExamQuestion.builder().jlptLevel("N5").questionText("[ReviewIT] Chọn trợ từ")
                .sentence("パン（　　）食べます。").optionA("が").optionB("を").optionC("に").optionD("で")
                .correctOption("B").questionType(JlptQuestionType.GRAMMAR_FORM).status(ExamQuestionStatus.DRAFT)
                .flag(ExamQuestionFlag.AMBIGUOUS).reviewNote("Máy giải lại: A cũng có thể đúng")
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
                JlptQuestionType.GRAMMAR_FORM, ExamQuestionStatus.DRAFT, true, particle.getId());
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
                .filter(stats -> stats.jlptLevel().equals("N5")).findFirst().orElseThrow();
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

    private List<Long> examPool() {
        return questionRepository.findRandomByLevelAndType("N5", JlptQuestionType.GRAMMAR_FORM, 1000).stream()
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
