package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gắn lại câu từ vựng với kho từ qua HTTP trên PostgreSQL thật: chỉ quản trị viên; chạy thử không ghi; câu đã gắn từ
 * và câu ngữ pháp không bị đụng tới.
 */
@AutoConfigureMockMvc
class AdminWordRelinkIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/exam-questions/relink-words";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private KanjiRepository kanjiRepository;

    private final List<Long> questionIds = new ArrayList<>();
    private final List<Long> extraWords = new ArrayList<>();
    private Kanji word;
    private Kanji otherWord;
    private ExamQuestion unlinked;
    private ExamQuestion missingWord;
    private ExamQuestion alreadyLinked;
    private ExamQuestion grammar;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime() % 100_000);
        word = kanjiRepository.save(Kanji.builder().character("延期" + suffix).reading("えんき").meaning("hoãn")
                .hanViet("").strokeCount(1).jlptLevel(JlptLevel.N3).build());
        otherWord = kanjiRepository.save(Kanji.builder().character("中止" + suffix).reading("ちゅうし").meaning("hủy")
                .hanViet("").strokeCount(1).jlptLevel(JlptLevel.N3).build());
        unlinked = save(question(JlptQuestionType.CONTEXT, word.getCharacter()));
        missingWord = save(question(JlptQuestionType.CONTEXT, "未登録" + suffix));
        ExamQuestion linked = question(JlptQuestionType.CONTEXT, word.getCharacter());
        linked.getKanjiIds().add(otherWord.getId());
        alreadyLinked = save(linked);
        grammar = save(question(JlptQuestionType.GRAMMAR_FORM, word.getCharacter()));
    }

    @AfterEach
    void tearDown() {
        questionRepository.deleteAllById(questionIds);
        kanjiRepository.deleteAllById(List.of(word.getId(), otherWord.getId()));
        kanjiRepository.deleteAllById(extraWords);
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotRelinkWords() throws Exception {
        mockMvc.perform(post(URL).param("dryRun", "false")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldCheckFirst_thenLinkOnlyTheVocabularyQuestionsWithoutWords() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true));
        assertThat(words().get(unlinked.getId())).as("chạy thử không ghi gì").isEmpty();

        mockMvc.perform(post(URL).param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false));

        Map<Long, Set<Long>> words = words();
        assertThat(words.get(unlinked.getId())).containsExactly(word.getId());
        assertThat(words.get(missingWord.getId())).isEmpty();
        assertThat(words.get(alreadyLinked.getId())).as("câu đã gắn từ giữ nguyên").containsExactly(otherWord.getId());
        assertThat(words.get(grammar.getId())).as("câu ngữ pháp không gắn từ vựng").isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void conjugatedOrKanaWords_shouldBeLinkedToTheStoredForm_andListedForReview() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 100_000);
        // Cách đọc riêng cho từng lần chạy để không trùng cách đọc của từ khác trong kho test.
        String kana = kana(System.nanoTime());
        Kanji suru = kanjiRepository.save(Kanji.builder().character("連絡" + suffix + "する").reading("れんらくする")
                .meaning("liên lạc").hanViet("").strokeCount(1).jlptLevel(JlptLevel.N3).build());
        Kanji kanjiWord = kanjiRepository.save(Kanji.builder().character("優雅" + suffix).reading(kana)
                .meaning("tao nhã").hanViet("").strokeCount(1).jlptLevel(JlptLevel.N3).build());
        extraWords.add(suru.getId());
        extraWords.add(kanjiWord.getId());
        ExamQuestion noun = save(question(JlptQuestionType.CONTEXT, "連絡" + suffix));
        ExamQuestion paraphrase = question(JlptQuestionType.PARAPHRASE, "上品な");
        paraphrase.setHighlight(kana);
        ExamQuestion written = save(paraphrase);

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inferred[?(@.written == '連絡" + suffix + "')].words")
                        .value("「連絡" + suffix + "する」"));

        mockMvc.perform(post(URL).param("dryRun", "false")).andExpect(status().isOk());

        Map<Long, Set<Long>> words = words();
        assertThat(words.get(noun.getId())).containsExactly(suru.getId());
        assertThat(words.get(written.getId())).as("viết kana, kho viết chữ Hán: theo cách đọc")
                .containsExactly(kanjiWord.getId());
    }

    /** Một chuỗi hiragana 8 chữ suy từ {@code seed}. */
    private static String kana(long seed) {
        StringBuilder kana = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            kana.append((char) ('あ' + (int) Math.floorMod(seed >> (i * 5), 40L)));
        }
        return kana.toString();
    }

    private Map<Long, Set<Long>> words() {
        return questionRepository.findAllWithWordsByIdIn(questionIds).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, ExamQuestion::getKanjiIds, (a, b) -> a));
    }

    private ExamQuestion save(ExamQuestion question) {
        ExamQuestion saved = questionRepository.save(question);
        questionIds.add(saved.getId());
        return saved;
    }

    /** Câu nhập từ đề, đáp án đúng (B) là {@code answer}. */
    private static ExamQuestion question(JlptQuestionType type, String answer) {
        return ExamQuestion.builder().jlptLevel(JlptLevel.N3).questionType(type)
                .questionText("（　　）に 入る ことばを えらんで ください。").sentence("雨のため、試合は（　　）された。")
                .optionA("開始").optionB(answer).optionC("参加").optionD("出発").correctOption("B")
                .review(new ReviewState(ExamQuestionStatus.APPROVED)).source(ExamQuestionSource.IMPORTED).build();
    }
}
