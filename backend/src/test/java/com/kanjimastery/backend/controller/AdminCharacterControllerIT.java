package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.CharacterRebuildReport;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tách lại Hán Việt từng chữ qua HTTP trên PostgreSQL thật (V35), cho các từ nhập thẳng vào DB như khi import bằng SQL:
 * chỉ quản trị viên; chạy thử không ghi; chạy thật lần hai không đổi gì.
 */
@AutoConfigureMockMvc
class AdminCharacterControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/characters/rebuild";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KanjiRepository kanjiRepository;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        kanjiRepository.deleteAllById(created);
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotRebuild() throws Exception {
        mockMvc.perform(post(URL).param("dryRun", "false")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldSeeTheReportFirst_thenSplitEveryWord_andARerunChangesNothing() throws Exception {
        Long bank = insert("銀行", "NGÂN HÀNG");
        Long people = insert("人々", "NHÂN NHÂN");
        Long oneSyllable = insert("一人", "NHẤT");
        insert("テレビ", "");

        CharacterRebuildReport dryRun = rebuild(true);

        assertThat(dryRun.dryRun()).isTrue();
        assertThat(dryRun.skipped()).filteredOn(word -> created.contains(word.id()))
                .extracting(CharacterRebuildReport.SkippedWord::id, CharacterRebuildReport.SkippedWord::reason)
                .containsExactly(tuple(oneSyllable, "COUNT_MISMATCH"));
        assertThat(kanjiRepository.findCharacterPartsOf(created)).as("chạy thử không ghi gì").isEmpty();

        CharacterRebuildReport run = rebuild(false);

        assertThat(run.changed()).isEqualTo(dryRun.changed());
        assertThat(kanjiRepository.findCharacterPartsOf(created))
                .extracting(KanjiRepository.CharacterPart::getWordId, KanjiRepository.CharacterPart::getCharacter,
                        KanjiRepository.CharacterPart::getHanViet)
                .containsExactly(tuple(bank, "銀", "NGÂN"), tuple(bank, "行", "HÀNG"), tuple(people, "人", "NHÂN"));
        assertThat(rebuild(false).changed()).isZero();
    }

    private CharacterRebuildReport rebuild(boolean dryRun) throws Exception {
        String body = mockMvc.perform(post(URL).param("dryRun", String.valueOf(dryRun)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(body, CharacterRebuildReport.class);
    }

    /** Ghi thẳng như một dòng nhập bằng SQL: chưa có chữ thành phần nào. */
    private Long insert(String character, String hanViet) {
        Long id = kanjiRepository.save(Kanji.builder().character(character).hanViet(hanViet).meaning("IT")
                .strokeCount(1).jlptLevel(JlptLevel.N5).build()).getId();
        created.add(id);
        return id;
    }
}
