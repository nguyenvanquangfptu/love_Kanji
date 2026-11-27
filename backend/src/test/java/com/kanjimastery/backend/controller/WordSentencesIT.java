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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Câu dùng một từ qua HTTP trên PostgreSQL thật: câu ví dụ, rồi câu thi đã duyệt gắn với từ; câu nháp không lộ ra. */
@AutoConfigureMockMvc
class WordSentencesIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private KanjiRepository kanjiRepository;

    private final List<Long> questionIds = new ArrayList<>();
    private Kanji word;

    @BeforeEach
    void setUp() {
        word = kanjiRepository.save(Kanji.builder().character("延期").reading("えんき").meaning("hoãn")
                .exampleSentence("会議を来週に延期した。").hanViet("").strokeCount(1).jlptLevel(JlptLevel.N3).build());
        save(question(ExamQuestionStatus.APPROVED, "雨で試合が（　　）になった。"));
        save(question(ExamQuestionStatus.DRAFT, "旅行が（　　）になった。"));
    }

    @AfterEach
    void tearDown() {
        questionRepository.deleteAllById(questionIds);
        kanjiRepository.deleteById(word.getId());
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldGetTheExample_thenApprovedExamSentencesFilledIn() throws Exception {
        mockMvc.perform(get("/api/v1/kanji/{id}/sentences", word.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sentence").value("会議を来週に延期した。"))
                .andExpect(jsonPath("$[0].source").value("EXAMPLE"))
                .andExpect(jsonPath("$[1].sentence").value("雨で試合が延期になった。"))
                .andExpect(jsonPath("$[1].source").value("EXAM"))
                .andExpect(jsonPath("$[1].segments[?(@.target == true)].text").value("延期"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void unknownWords_shouldBeNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/kanji/{id}/sentences", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private void save(ExamQuestion question) {
        questionIds.add(questionRepository.save(question).getId());
    }

    private ExamQuestion question(ExamQuestionStatus status, String sentence) {
        ExamQuestion question = ExamQuestion.builder().jlptLevel(JlptLevel.N3).questionType(JlptQuestionType.CONTEXT)
                .questionText("（　　）に 入る ことばを えらんで ください。").sentence(sentence)
                .optionA("開始").optionB("延期").optionC("参加").optionD("出発").correctOption("B")
                .review(new ReviewState(status)).source(ExamQuestionSource.IMPORTED).build();
        question.getKanjiIds().add(word.getId());
        return question;
    }
}
