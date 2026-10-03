package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API danh sách ngữ pháp qua HTTP: chỉ quản trị viên dùng được; nhập CSV, đọc danh sách, xuất CSV. */
@AutoConfigureMockMvc
class AdminGrammarControllerIT extends AbstractIntegrationTest {

    private static final String CSV = """
            cap_do,bai,mau,nghia,cach_noi
            N4,N4-34,〜とおりに,"Làm đúng như, theo như",Vた／Nの + とおりに
            N4,N4-46,〜はずです,Chắc chắn là ~,普通形 + はずです
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GrammarPointRepository grammarPointRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        grammarPointRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotReachTheGrammarAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/grammar-points")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldImportListAndExportTheGrammarList() throws Exception {
        mockMvc.perform(post("/api/v1/admin/grammar-points/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("csv", CSV))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.errors").isEmpty());

        mockMvc.perform(get("/api/v1/admin/grammar-points").param("level", "N4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pattern").value("〜とおりに"))
                .andExpect(jsonPath("$[0].meaningVi").value("Làm đúng như, theo như"))
                .andExpect(jsonPath("$[0].approvedQuestions").value(0));

        byte[] exported = mockMvc.perform(get("/api/v1/admin/grammar-points/export").param("level", "N4"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(exported, StandardCharsets.UTF_8);
        // BOM để Excel đọc đúng chữ; phần sau đọc lại được bằng chính chức năng nhập.
        assertThat(csv.charAt(0)).isEqualTo((char) 0xFEFF);
        assertThat(csv).contains("N4,N4-34,〜とおりに,\"Làm đúng như, theo như\",Vた／Nの + とおりに,");
    }
}
