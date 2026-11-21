package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.ExamImportRequest;
import com.kanjimastery.backend.dto.ExamImportResponse;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ReviewState;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

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
    /** Mã của câu và đoạn văn dựng sẵn trong kho để so độ giống. */
    private static final String BANK = "IT-BANK";
    /** Đoạn văn 文章の文法 trong file đề: chỗ trống đánh theo số câu trong đề (19-23). */
    private static final String PASSAGE_IN_TEST = "私の町の駅前には、古い商店街がある。【19】、多くの店が閉まってしまったが、"
            + "最近は若い人たちが新しい店を開く【20】。週末になると、遠くから来る客も【21】。商店街の人たちは、町を明るく"
            + "するために、毎月祭りを【22】。これからも、この商店街を大切に【23】と思う。";
    /** Cùng đoạn văn như được lưu trong kho: chỗ trống đánh lại 【1】..【5】. */
    private static final String PASSAGE = PASSAGE_IN_TEST.replace("【19】", "【1】").replace("【20】", "【2】")
            .replace("【21】", "【3】").replace("【22】", "【4】").replace("【23】", "【5】");

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
        questionRepository.deleteAll(withRef(questionRepository.findAll(), ExamQuestion::getSourceRef, BANK));
        passageRepository.deleteAll(withRef(passageRepository.findAll(), ExamPassage::getSourceRef, BANK));
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void contentCloseToTheBank_shouldBeWarnedAbout_butStillImported() throws Exception {
        ExamQuestion stored = questionRepository.save(bankQuestion(1, JlptLevel.N3, ExamQuestionStatus.DRAFT,
                "雨のため、運動会は来週に延期された。"));
        // Giống hệt câu / đoạn văn của đề, nhưng đã bị loại hoặc ở cấp độ khác: không tính.
        ExamQuestion rejected = questionRepository.save(bankQuestion(2, JlptLevel.N3, ExamQuestionStatus.REJECTED,
                "台風のため、運動会は来週に延期された。"));
        ExamQuestion otherLevel = questionRepository.save(bankQuestion(3, JlptLevel.N2, ExamQuestionStatus.DRAFT,
                "台風のため、運動会は来週に延期された。"));
        ExamPassage storedPassage = passageRepository.save(bankPassage(1, ExamQuestionStatus.DRAFT,
                PASSAGE.replace("古い", "大きな")));
        ExamPassage rejectedPassage = passageRepository.save(bankPassage(2, ExamQuestionStatus.REJECTED, PASSAGE));
        var test = ExamImportFixtures.n3Test(CODE);
        test.vocabulary().kanjiReading().set(0, ExamImportFixtures.item(1, "台風のため、運動会は来週に延期された。", "延期",
                null, List.of("えんき", "えんちょう", "ていき", "ていし"), 1, null, null));
        String json = objectMapper.writeValueAsString(new ExamImportRequest(CODE, "N3", test.vocabulary(),
                ExamImportFixtures.grammar(ExamImportFixtures.passage(PASSAGE_IN_TEST)), null));

        MvcResult checked = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andReturn();

        List<String> warnings = objectMapper.readValue(checked.getResponse().getContentAsString(StandardCharsets.UTF_8),
                ExamImportResponse.class).warnings();
        assertThat(warnings).contains("TV câu 1: gần giống câu đã có trong kho (82%, " + BANK + "/TV/1 #"
                + stored.getId() + "): 「雨のため、運動会は来週に延期された。」");
        assertThat(warnings).filteredOn(warning -> warning.startsWith("NP đoạn văn: gần giống đoạn văn đã có trong kho"))
                .singleElement().asString().contains("#" + storedPassage.getId());
        assertThat(warnings).noneMatch(warning -> warning.contains("#" + rejected.getId())
                || warning.contains("#" + otherLevel.getId()) || warning.contains("#" + rejectedPassage.getId()));

        mockMvc.perform(post(URL).param("dryRun", "false").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(true))
                .andExpect(jsonPath("$.questions").value(58));
    }

    /** Câu 漢字読み đã có trong kho, mã {@code IT-BANK/TV/<number>}. */
    private static ExamQuestion bankQuestion(int number, JlptLevel level, ExamQuestionStatus status, String sentence) {
        return ExamQuestion.builder().jlptLevel(level).questionText("Chọn cách đọc đúng của từ được gạch chân.")
                .sentence(sentence).highlight("延期").optionA("えんき").optionB("えんちょう").optionC("ていき")
                .optionD("ていし").correctOption("A").questionType(JlptQuestionType.KANJI_READING)
                .review(new ReviewState(status)).source(ExamQuestionSource.IMPORTED).sourceRef(BANK + "/TV/" + number)
                .build();
    }

    /** Đoạn 文章の文法 N3 đã có trong kho, mã {@code IT-BANK/NP/<number>}. */
    private static ExamPassage bankPassage(int number, ExamQuestionStatus status, String content) {
        return ExamPassage.builder().jlptLevel(JlptLevel.N3).section(ExamSection.GRAMMAR).content(content)
                .review(new ReviewState(status)).source(ExamQuestionSource.IMPORTED).sourceRef(BANK + "/NP/" + number)
                .build();
    }

    private String testJson() throws Exception {
        return objectMapper.writeValueAsString(ExamImportFixtures.n3Test(CODE));
    }

    private List<ExamQuestion> imported() {
        return withRef(questionRepository.findAll(), ExamQuestion::getSourceRef, CODE);
    }

    private List<ExamPassage> importedPassages() {
        return withRef(passageRepository.findAll(), ExamPassage::getSourceRef, CODE);
    }

    /** Các dòng có mã đề gốc bắt đầu bằng {@code code}/. */
    private static <T> List<T> withRef(List<T> rows, Function<T, String> sourceRef, String code) {
        return rows.stream()
                .filter(row -> sourceRef.apply(row) != null && sourceRef.apply(row).startsWith(code + "/"))
                .toList();
    }
}
