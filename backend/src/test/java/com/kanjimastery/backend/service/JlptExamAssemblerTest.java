package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static com.kanjimastery.backend.model.JlptQuestionType.CONTEXT;
import static com.kanjimastery.backend.model.JlptQuestionType.KANJI_READING;
import static com.kanjimastery.backend.model.JlptQuestionType.ORTHOGRAPHY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JlptExamAssemblerTest {

    @Mock
    private ExamQuestionRepository questionRepository;

    @InjectMocks
    private JlptExamAssembler assembler;

    @Test
    void assemble_shouldFillEachMondaiInOrder_askingEachWordOnceInTheWholeSitting() {
        JlptBlueprintProperties.Section section = new JlptBlueprintProperties.Section();
        section.setName(ExamSection.VOCABULARY);
        section.setMinutes(10);
        section.setQuestions(new LinkedHashMap<>());
        section.getQuestions().put(KANJI_READING, 2);
        section.getQuestions().put(ORTHOGRAPHY, 1);
        section.getQuestions().put(CONTEXT, 2);
        // Từ 11 đã hỏi ở phần trước của buổi thi.
        Set<Long> askedWords = new HashSet<>(Set.of(11L));
        List<ExamQuestion> reading = List.of(question(1L, 10L), question(2L, 11L), question(3L, 12L), question(4L, 13L));
        List<ExamQuestion> context = List.of(question(5L, 12L), question(6L, 14L), question(7L, 15L), question(8L, 16L));
        when(questionRepository.findRandomByLevelAndType("N4", KANJI_READING, 24)).thenReturn(reading);
        when(questionRepository.findAllWithWordsByIdIn(List.of(1L, 2L, 3L, 4L))).thenReturn(reading);
        when(questionRepository.findRandomByLevelAndType("N4", ORTHOGRAPHY, 22)).thenReturn(List.of());
        when(questionRepository.findRandomByLevelAndType("N4", CONTEXT, 24)).thenReturn(context);
        when(questionRepository.findAllWithWordsByIdIn(List.of(5L, 6L, 7L, 8L))).thenReturn(context);

        List<JlptExamAssembler.Mondai> mondai = assembler.assemble("N4", section, askedWords);

        // 問題1 bỏ câu 2 (từ 11 đã hỏi); 問題2 chưa có câu; 問題3 bỏ câu 5 (từ 12 vừa hỏi ở 問題1) và đủ 2 câu thì thôi.
        assertThat(mondai).extracting(JlptExamAssembler.Mondai::number, JlptExamAssembler.Mondai::type,
                        JlptExamAssembler.Mondai::plannedCount)
                .containsExactly(tuple(1, KANJI_READING, 2), tuple(2, ORTHOGRAPHY, 1), tuple(3, CONTEXT, 2));
        assertThat(mondai.get(0).questions()).extracting(ExamQuestion::getId).containsExactly(1L, 3L);
        assertThat(mondai.get(1).questions()).isEmpty();
        assertThat(mondai.get(2).questions()).extracting(ExamQuestion::getId).containsExactly(6L, 7L);
        assertThat(askedWords).containsExactlyInAnyOrder(11L, 10L, 12L, 14L, 15L);
    }

    private static ExamQuestion question(Long id, Long kanjiId) {
        return ExamQuestion.builder().id(id).jlptLevel("N4").questionText("Câu " + id).optionA("1").optionB("2")
                .optionC("3").optionD("4").correctOption("A").kanjiIds(Set.of(kanjiId)).build();
    }
}
