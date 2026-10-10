package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.service.ExamImportFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập một đề tự soạn qua HTTP trên PostgreSQL thật (V32): chỉ quản trị viên; chạy thử không ghi; nhập lại không trùng. */
@AutoConfigureMockMvc
class AdminExamImportIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/exam-questions/import";
    private static final String CODE = "IT-IMPORT";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private ExamPassageRepository passageRepository;

    @AfterEach
    void tearDown() {
        questionRepository.deleteAll(imported());
        passageRepository.deleteAll(importedPassages());
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotImportTests() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(testJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldCheckATestFirst_thenImportItOnlyOnce() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(testJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.imported").value(false))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.questions").value(58))
                .andExpect(jsonPath("$.passages").value(1));
        assertThat(imported()).as("chạy thử không ghi gì").isEmpty();

        mockMvc.perform(post(URL).param("dryRun", "false").contentType(MediaType.APPLICATION_JSON).content(testJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(true))
                .andExpect(jsonPath("$.questions").value(58))
                .andExpect(jsonPath("$.passages").value(1));
        List<ExamQuestion> questions = imported();
        assertThat(questions).hasSize(58).allSatisfy(question -> {
            assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.IMPORTED);
        });
        ExamPassage passage = importedPassages().get(0);
        assertThat(passage.getContent()).isEqualTo("【1】、【2】、【3】、【4】、【5】。");
        assertThat(questions).filteredOn(question -> question.getBlankNo() != null)
                .hasSize(5)
                .allSatisfy(question -> assertThat(question.getPassageId()).isEqualTo(passage.getId()));

        mockMvc.perform(post(URL).param("dryRun", "false").contentType(MediaType.APPLICATION_JSON).content(testJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").value(0))
                .andExpect(jsonPath("$.passages").value(0))
                .andExpect(jsonPath("$.alreadyImported").value(58));
        assertThat(imported()).as("nhập lại không tạo câu trùng").hasSize(58);
        assertThat(importedPassages()).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anImportedTest_shouldBeListedByItsCode_inTheOrderOfTheTest() throws Exception {
        mockMvc.perform(post(URL).param("dryRun", "false").contentType(MediaType.APPLICATION_JSON).content(testJson()))
                .andExpect(jsonPath("$.imported").value(true));

        // 53 câu đứng riêng; 5 câu của đoạn văn được duyệt theo cả đoạn ở danh sách đoạn văn.
        mockMvc.perform(get("/api/v1/admin/exam-questions").param("level", "N3").param("test", CODE)
                        .param("size", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(53))
                .andExpect(jsonPath("$.content[0].sourceRef").value(CODE + "/TV/1"))
                .andExpect(jsonPath("$.content[0].source").value("IMPORTED"))
                .andExpect(jsonPath("$.content[52].sourceRef").value(CODE + "/NP/18"));
        mockMvc.perform(get("/api/v1/admin/exam-questions").param("level", "N3").param("test", "IT-KHONG-CO"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aTestWithMistakes_shouldBeReportedAndNotImported() throws Exception {
        var test = ExamImportFixtures.n3Test(CODE);
        test.vocabulary().kanjiReading().remove(0);

        mockMvc.perform(post(URL).param("dryRun", "false").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(test)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(false))
                .andExpect(jsonPath("$.errors[0]").value("TV 問題1 (KANJI_READING): cần 8 câu, file có 7"));
        assertThat(imported()).isEmpty();
    }

    private String testJson() throws Exception {
        return objectMapper.writeValueAsString(ExamImportFixtures.n3Test(CODE));
    }

    private List<ExamQuestion> imported() {
        return questionRepository.findAll().stream()
                .filter(question -> question.getSourceRef() != null && question.getSourceRef().startsWith(CODE + "/"))
                .toList();
    }

    private List<ExamPassage> importedPassages() {
        return passageRepository.findAll().stream()
                .filter(passage -> passage.getSourceRef() != null && passage.getSourceRef().startsWith(CODE + "/"))
                .toList();
    }
}
