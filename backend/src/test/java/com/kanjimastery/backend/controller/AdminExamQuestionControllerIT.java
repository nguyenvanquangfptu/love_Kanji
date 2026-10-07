package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API duyệt câu thi qua HTTP: chỉ quản trị viên; lọc, đổi trạng thái, thống kê, sinh nháp. */
@AutoConfigureMockMvc
// Không bao giờ gọi Gemini thật trong test, kể cả khi máy có sẵn GEMINI_API_KEY.
@TestPropertySource(properties = "app.ai.gemini-api-key=")
class AdminExamQuestionControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExamQuestionRepository questionRepository;

    private ExamQuestion draft;

    @BeforeEach
    void setUp() {
        draft = questionRepository.save(ExamQuestion.builder().jlptLevel("N4").questionText("[ControllerIT] Chọn")
                .sentence("駅（　　）行きます。").optionA("へ").optionB("を").optionC("が").optionD("の")
                .correctOption("A").questionType(JlptQuestionType.GRAMMAR_FORM).status(ExamQuestionStatus.DRAFT)
                .build());
    }

    @AfterEach
    void tearDown() {
        questionRepository.deleteById(draft.getId());
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotReachTheReviewPage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-questions")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exam-questions/{id}/status", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exam-questions/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\": [" + draft.getId() + "]}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exam-questions/analysis")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exam-questions/{id}/reports/dismiss", draft.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldRunTheQuestionAnalysis_andDismissReports() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exam-questions/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzed").isNumber())
                .andExpect(jsonPath("$.flagged").value(0));
        mockMvc.perform(post("/api/v1/admin/exam-questions/{id}/reports/dismiss", draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldApproveAPageOfDraftsAtOnce() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exam-questions/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": []}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/exam-questions/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [" + draft.getId() + ", -1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.skipped[0].id").value(-1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldListDraftsApproveThemAndSeeTheBank() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-questions").param("level", "N4")
                        .param("type", JlptQuestionType.GRAMMAR_FORM.name()).param("status", ExamQuestionStatus.DRAFT.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(draft.getId()))
                .andExpect(jsonPath("$.content[0].correctOption").value("A"));

        mockMvc.perform(post("/api/v1/admin/exam-questions/{id}/status", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"REJECTED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/exam-questions/{id}/status", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ExamQuestionStatus.APPROVED.name()));

        mockMvc.perform(get("/api/v1/admin/exam-questions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.jlptLevel == 'N4')].types[?(@.type == 'GRAMMAR_FORM')].perExam").value(13));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void drafting_shouldExplainThatGeminiIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exam-questions/drafts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grammarPointId\": 1, \"type\": \"GRAMMAR_FORM\", \"count\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("GEMINI_API_KEY")));
        mockMvc.perform(post("/api/v1/admin/exam-questions/vocabulary-drafts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\": \"N4\", \"type\": \"USAGE\", \"count\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("GEMINI_API_KEY")));
        mockMvc.perform(post("/api/v1/admin/exam-questions/vocabulary-drafts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"USAGE\", \"count\": 9}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("level:")))
                .andExpect(content().string(containsString("count:")));
    }
}
