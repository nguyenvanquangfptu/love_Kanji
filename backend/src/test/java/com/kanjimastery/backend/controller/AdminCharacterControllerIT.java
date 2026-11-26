package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.CharacterRebuildReport;
import com.kanjimastery.backend.dto.HanVietRequest;
import com.kanjimastery.backend.dto.VocabularyQualityReport;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tách lại Hán Việt từng chữ qua HTTP trên PostgreSQL thật (V35), cho các từ nhập thẳng vào DB như khi import bằng SQL:
 * chỉ quản trị viên; chạy thử không ghi; chạy thật lần hai không đổi gì.
 */
@AutoConfigureMockMvc
class AdminCharacterControllerIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/characters";
    private static final String URL = BASE + "/rebuild";

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

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotSeeTheQualityReport_norFixIt() throws Exception {
        mockMvc.perform(get(BASE + "/quality")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/stroke-counts/fix").param("dryRun", "false")).andExpect(status().isForbidden());
    }

    /** Số nét tính từ bảng kanji_strokes (V41, KANJIDIC2): 時 10 + 計 9; 人 2 + 々 3. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldSeeWrongStrokeCounts_thenFixThemFromTheStrokeTable() throws Exception {
        Long clock = insert("時計", "THỜI KẾ");
        Long people = insert("人々", "NHÂN");

        VocabularyQualityReport report = quality();
        assertThat(report.strokeMismatches()).filteredOn(word -> created.contains(word.id()))
                .extracting(VocabularyQualityReport.StrokeMismatch::id, VocabularyQualityReport.StrokeMismatch::computed)
                .containsExactlyInAnyOrder(tuple(clock, 19), tuple(people, 5));

        VocabularyQualityReport.StrokeFix dryRun = fixStrokes(true);
        assertThat(dryRun.changed()).isGreaterThanOrEqualTo(2);
        assertThat(kanjiRepository.findById(clock).orElseThrow().getStrokeCount()).as("chạy thử không ghi").isEqualTo(1);

        fixStrokes(false);
        assertThat(kanjiRepository.findAllById(List.of(clock, people)))
                .extracting(Kanji::getId, Kanji::getStrokeCount)
                .containsExactlyInAnyOrder(tuple(clock, 19), tuple(people, 5));
        assertThat(quality().strokeMismatches()).noneMatch(word -> created.contains(word.id()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldSeeAKanjiWrittenWithSeveralHanViet_andFixAWordInPlace() throws Exception {
        Long apply = insert("申し込む", "");
        Long dive = insert("飛び込む", "");
        updateHanViet(apply, "thân nhập").andExpect(status().isOk());
        updateHanViet(dive, "PHI VÀO").andExpect(status().isOk());

        assertThat(quality().hanVietConflicts()).filteredOn(conflict -> conflict.character().equals("込"))
                .singleElement()
                .satisfies(conflict -> assertThat(conflict.variants())
                        .flatExtracting(VocabularyQualityReport.Variant::words)
                        .filteredOn(word -> created.contains(word.id()))
                        .extracting(VocabularyQualityReport.WordRef::id, VocabularyQualityReport.WordRef::hanViet)
                        .containsExactlyInAnyOrder(tuple(apply, "THÂN NHẬP"), tuple(dive, "PHI VÀO")));

        updateHanViet(apply, "THÂN").andExpect(status().isBadRequest());
        updateHanViet(apply, "THÂN VÀO").andExpect(status().isOk());
        assertThat(kanjiRepository.findCharacterPartsOf(List.of(apply)))
                .extracting(KanjiRepository.CharacterPart::getCharacter, KanjiRepository.CharacterPart::getHanViet)
                .containsExactly(tuple("申", "THÂN"), tuple("込", "VÀO"));
    }

    private VocabularyQualityReport quality() throws Exception {
        String body = mockMvc.perform(get(BASE + "/quality"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(body, VocabularyQualityReport.class);
    }

    private VocabularyQualityReport.StrokeFix fixStrokes(boolean dryRun) throws Exception {
        String body = mockMvc.perform(post(BASE + "/stroke-counts/fix").param("dryRun", String.valueOf(dryRun)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(body, VocabularyQualityReport.StrokeFix.class);
    }

    private ResultActions updateHanViet(Long id, String hanViet) throws Exception {
        return mockMvc.perform(put(BASE + "/words/" + id + "/han-viet").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new HanVietRequest(hanViet))));
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
