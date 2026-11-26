package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.VocabularyImportRequest;
import com.kanjimastery.backend.dto.VocabularyImportRequest.Word;
import com.kanjimastery.backend.dto.VocabularyImportResponse;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.TagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập từ vựng một bài qua HTTP trên PostgreSQL thật: chỉ quản trị viên; chạy thử không ghi; nhập lại không trùng. */
@AutoConfigureMockMvc
class AdminVocabularyImportIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/kanji/import";
    private static final String LESSON = "IT-VOCAB";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM kanji WHERE character IN ('冒険', '冒険者') AND meaning LIKE 'IT %'");
        tagRepository.findByName(LESSON).ifPresent(tagRepository::delete);
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotImportVocabulary() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldCheckFirst_thenImport_andAReimportChangesNothing() throws Exception {
        kanjiRepository.save(Kanji.builder().character("冒険").reading("ぼうけん").hanViet("MẠO HIỂM")
                .meaning("IT Mạo hiểm").strokeCount(1).jlptLevel(JlptLevel.N3).build());

        VocabularyImportResponse check = send(true);
        assertThat(check.errors()).isEmpty();
        assertThat(check.created()).isEqualTo(1);
        assertThat(check.linked()).isEqualTo(1);
        assertThat(tagRepository.findByName(LESSON)).as("chạy thử không tạo bài").isEmpty();

        VocabularyImportResponse run = send(false);
        assertThat(run.imported()).isTrue();
        Long lessonId = tagRepository.findByName(LESSON).orElseThrow().getId();
        List<Kanji> words = kanjiRepository.search(null, null, lessonId, PageRequest.of(0, 10)).getContent();
        assertThat(words).extracting(Kanji::getCharacter, Kanji::getStrokeCount, Kanji::getJlptLevel)
                .containsExactlyInAnyOrder(
                        tuple("冒険", 1, JlptLevel.N3),
                        // 冒 9 + 険 11 + 者 8, tính từ bảng kanji_strokes (V41).
                        tuple("冒険者", 28, JlptLevel.N2));
        assertThat(kanjiRepository.findCharacterPartsOf(words.stream()
                        .filter(word -> word.getCharacter().equals("冒険者")).map(Kanji::getId).toList()))
                .extracting(KanjiRepository.CharacterPart::getCharacter, KanjiRepository.CharacterPart::getHanViet)
                .containsExactly(tuple("冒", "MẠO"), tuple("険", "HIỂM"), tuple("者", "GIẢ"));

        VocabularyImportResponse again = send(false);
        assertThat(again.created()).isZero();
        assertThat(again.linked()).isZero();
        assertThat(again.alreadyInLesson()).isEqualTo(2);
    }

    private VocabularyImportResponse send(boolean dryRun) throws Exception {
        String response = mockMvc.perform(post(URL).param("dryRun", String.valueOf(dryRun))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(response, VocabularyImportResponse.class);
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(new VocabularyImportRequest(LESSON, "N2", List.of(
                new Word("冒険", "ぼうけん", "MẠO HIỂM", "Mạo hiểm", "名詞", null, null, null),
                new Word("冒険者", "ぼうけんしゃ", "mạo hiểm giả", "IT Nhà thám hiểm", "名詞", null, null, null))));
    }
}
