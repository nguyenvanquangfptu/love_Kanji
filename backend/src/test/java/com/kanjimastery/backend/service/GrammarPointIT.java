package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.GrammarImportResult;
import com.kanjimastery.backend.dto.GrammarPointResponse;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Nhập danh sách ngữ pháp vào PostgreSQL thật: nhập lại không trùng, đếm câu thi theo trạng thái duyệt. */
class GrammarPointIT extends AbstractIntegrationTest {

    /** Cấp độ thật nhưng các bài giả, xoá hết sau mỗi test. */
    private static final String LEVEL = "N5";

    @Autowired
    private GrammarPointService grammarPointService;
    @Autowired
    private GrammarPointRepository grammarPointRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;

    @AfterEach
    void tearDown() {
        questionRepository.deleteAll(questionRepository.findAll().stream()
                .filter(question -> question.getQuestionText().startsWith("[GrammarPointIT]"))
                .toList());
        grammarPointRepository.deleteAll();
    }

    @Test
    void importingTheSameFileTwice_shouldNotDuplicate_andTheListCountsQuestionsByStatus() {
        String csv = """
                cap_do,bai,mau,nghia,cach_noi
                N5,N5-01,〜は〜です,A là B,N1 は N2 です
                N5,N5-04,〜から〜まで,Từ ~ đến ~,N から N まで
                """;

        assertThat(grammarPointService.importCsv(csv)).isEqualTo(new GrammarImportResult(2, 0, 0, List.of()));
        assertThat(grammarPointService.importCsv(csv)).isEqualTo(new GrammarImportResult(0, 0, 2, List.of()));

        Long fromTo = grammarPointRepository.findByJlptLevelAndPattern(LEVEL, "〜から〜まで").orElseThrow().getId();
        question(ExamQuestionStatus.APPROVED, fromTo);
        question(ExamQuestionStatus.APPROVED, fromTo);
        question(ExamQuestionStatus.DRAFT, fromTo);

        List<GrammarPointResponse> points = grammarPointService.list(LEVEL);
        assertThat(points)
                .extracting(GrammarPointResponse::getLesson, GrammarPointResponse::getPattern,
                        GrammarPointResponse::getApprovedQuestions, GrammarPointResponse::getDraftQuestions)
                .containsExactly(tuple("N5-01", "〜は〜です", 0L, 0L), tuple("N5-04", "〜から〜まで", 2L, 1L));
    }

    private void question(String status, Long grammarPointId) {
        questionRepository.save(ExamQuestion.builder().jlptLevel(LEVEL).questionText("[GrammarPointIT] câu")
                .optionA("1").optionB("2").optionC("3").optionD("4").correctOption("A")
                .questionType(JlptQuestionType.GRAMMAR_FORM).status(status)
                .grammarPointIds(Set.of(grammarPointId)).build());
    }
}
