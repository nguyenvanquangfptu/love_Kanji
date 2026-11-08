package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API đoạn văn 文章の文法 qua HTTP: chỉ quản trị viên; xem kèm câu hỏi, sửa nội dung, duyệt cả đoạn, duyệt nhiều đoạn
 * một lượt, nhờ AI viết.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.gemini-api-key=")
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
        passage = passageRepository.save(ExamPassage.builder().jlptLevel(JlptLevel.N4).section(ExamSection.GRAMMAR).title("日記")
                .content("きのうは雨でした。【1】、出かけませんでした。").build());
        questionRepository.save(ExamQuestion.builder().jlptLevel(JlptLevel.N4).questionText("【1】")
                .optionA("だから").optionB("でも").optionC("それに").optionD("または").correctOption("A")
                .questionType(JlptQuestionType.TEXT_GRAMMAR).review(new ReviewState(ExamQuestionStatus.DRAFT))
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
        mockMvc.perform(post("/api/v1/admin/exam-passages/drafts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\": \"N4\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exam-passages/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\": [" + passage.getId() + "]}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exam-passages/approve-all").contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\": \"N4\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void bulkApproval_shouldApproveOnlyValidDraftsWithoutWarnings_andExplainTheRest() throws Exception {
        // Đoạn mất chỗ trống 【1】 mà câu hỏi vẫn trỏ tới chỗ trống 1.
        ExamPassage broken = savePassage("きのうは雨でした。出かけませんでした。", null);
        ExamPassage flagged = savePassage("あしたは晴れます。【1】、出かけます。", ExamQuestionFlag.AMBIGUOUS);
        try {
            String ids = passage.getId() + ", " + broken.getId() + ", " + flagged.getId() + ", -1";
            mockMvc.perform(post("/api/v1/admin/exam-passages/approve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\": [" + ids + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approved").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(3))
                    .andExpect(jsonPath("$.skipped[0].id").value(broken.getId()))
                    .andExpect(jsonPath("$.skipped[0].reason").value(containsString("cần sửa")))
                    .andExpect(jsonPath("$.skipped[1].id").value(flagged.getId()))
                    .andExpect(jsonPath("$.skipped[1].reason").value(containsString("cảnh báo")))
                    .andExpect(jsonPath("$.skipped[2].reason").value("không tìm thấy"));

            assertThat(passageRepository.findById(passage.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.APPROVED);
            assertThat(questionRepository.findAllWithLinksByPassageIdIn(List.of(passage.getId())))
                    .allMatch(question -> ExamQuestionStatus.APPROVED.equals(question.getStatus()));
            assertThat(passageRepository.findById(broken.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.DRAFT);
            assertThat(passageRepository.findById(flagged.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.DRAFT);

            // Đã duyệt rồi thì lượt sau bỏ qua.
            mockMvc.perform(post("/api/v1/admin/exam-passages/approve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\": [" + passage.getId() + "]}"))
                    .andExpect(jsonPath("$.approved").value(0))
                    .andExpect(jsonPath("$.skipped[0].reason").value("không ở trạng thái chờ duyệt"));
        } finally {
            passageRepository.deleteById(broken.getId());
            passageRepository.deleteById(flagged.getId());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveAll_shouldApproveEveryMatchingDraftPassage_notOnlyOnePage() throws Exception {
        ExamPassage broken = savePassage("きのうは雨でした。出かけませんでした。", null, null);
        ExamPassage flagged = savePassage("あしたは晴れます。【1】、出かけます。", ExamQuestionFlag.AMBIGUOUS, null);
        // Đoạn của một đề tự soạn: chỉ lọc đề đó thì đoạn setUp() (không có mã đề) không bị đụng tới.
        ExamPassage ofTest = savePassage("あしたは晴れます。【1】、出かけます。", null, "ZZ-IT/NP/19-19");
        try {
            mockMvc.perform(post("/api/v1/admin/exam-passages/approve-all").contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/v1/admin/exam-passages").param("level", "N4").param("test", "ZZ-IT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(ofTest.getId()));

            mockMvc.perform(post("/api/v1/admin/exam-passages/approve-all").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"level\": \"N4\", \"section\": \"GRAMMAR\", \"test\": \"ZZ-IT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approved").value(1))
                    .andExpect(jsonPath("$.skippedTotal").value(0));
            assertThat(passageRepository.findById(passage.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.DRAFT);

            mockMvc.perform(post("/api/v1/admin/exam-passages/approve-all").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"level\": \"N4\", \"section\": \"GRAMMAR\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approved").value(1))
                    .andExpect(jsonPath("$.skippedTotal").value(2))
                    .andExpect(jsonPath("$.skipped[0].id").value(broken.getId()))
                    .andExpect(jsonPath("$.skipped[1].id").value(flagged.getId()));
            assertThat(passageRepository.findById(passage.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.APPROVED);
            assertThat(passageRepository.findById(flagged.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExamQuestionStatus.DRAFT);
        } finally {
            passageRepository.deleteById(broken.getId());
            passageRepository.deleteById(flagged.getId());
            passageRepository.deleteById(ofTest.getId());
        }
    }

    /** Đoạn chờ duyệt có một câu hỏi ở chỗ trống 1; {@code questionFlag} khác null thì câu hỏi bị gắn cờ. */
    private ExamPassage savePassage(String content, ExamQuestionFlag questionFlag) {
        return savePassage(content, questionFlag, null);
    }

    private ExamPassage savePassage(String content, ExamQuestionFlag questionFlag, String sourceRef) {
        ExamPassage saved = passageRepository.save(ExamPassage.builder().jlptLevel(JlptLevel.N4)
                .section(ExamSection.GRAMMAR).content(content).sourceRef(sourceRef).build());
        questionRepository.save(ExamQuestion.builder().jlptLevel(JlptLevel.N4).questionText("【1】")
                .optionA("だから").optionB("でも").optionC("それに").optionD("または").correctOption("A")
                .questionType(JlptQuestionType.TEXT_GRAMMAR)
                .review(new ReviewState(ExamQuestionStatus.DRAFT, questionFlag, null, null))
                .passageId(saved.getId()).blankNo(1).build());
        return saved;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void drafting_shouldExplainThatGeminiIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exam-passages/drafts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\": \"N4\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("GEMINI_API_KEY")));
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
