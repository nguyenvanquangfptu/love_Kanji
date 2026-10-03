package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API đoạn văn 文章の文法 qua HTTP: chỉ quản trị viên; xem kèm câu hỏi, sửa nội dung, duyệt cả đoạn. */
@AutoConfigureMockMvc
class AdminExamPassageControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExamPassageRepository passageRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;

    private ExamPassage passage;

    @BeforeEach
    void setUp() {
        passage = passageRepository.save(ExamPassage.builder().jlptLevel("N4").title("日記")
                .content("きのうは雨でした。【1】、出かけませんでした。").build());
        questionRepository.save(ExamQuestion.builder().jlptLevel("N4").questionText("【1】")
                .optionA("だから").optionB("でも").optionC("それに").optionD("または").correctOption("A")
                .questionType(JlptQuestionType.TEXT_GRAMMAR).status(ExamQuestionStatus.DRAFT)
                .passageId(passage.getId()).blankNo(1).build());
    }

    @AfterEach
    void tearDown() {
        passageRepository.deleteById(passage.getId());
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotReachPassages() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-passages").param("level", "N4")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldSeePassagesWithTheirQuestions_editThem_andApproveThemWhole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-passages").param("level", "N4").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(passage.getId()))
                .andExpect(jsonPath("$.content[0].questions[0].blankNo").value(1));

        mockMvc.perform(put("/api/v1/admin/exam-passages/{id}", passage.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"日記\", \"content\": \"chỗ trống bị xoá mất\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/exam-passages/{id}/status", passage.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.questions[0].status").value("APPROVED"));
    }
}
