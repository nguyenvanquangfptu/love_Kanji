package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.PracticeQuestionResponse;
import com.kanjimastery.backend.dto.QuestionReviewItem;
import com.kanjimastery.backend.dto.WeakGrammarResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarPracticeServiceTest {

    @Mock
    private GrammarPointRepository grammarPointRepository;
    @Mock
    private ExamQuestionRepository questionRepository;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private GrammarPracticeService practiceService;

    @Test
    void practice_shouldNeedOneToTwentyGrammarPoints() {
        List<Long> tooMany = LongStream.rangeClosed(1, 21).boxed().toList();

        assertThatThrownBy(() -> practiceService.practice("N4", List.of()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> practiceService.practice("N4", new ArrayList<>(Arrays.asList((Long) null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> practiceService.practice("N4", tooMany))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("20");
        verify(questionRepository, never()).findRandomForGrammarPoints(anyString(), anyCollection(), anyInt());
    }

    @Test
    void practice_shouldBeEmpty_whenNoApprovedQuestionTestsThesePoints() {
        when(questionRepository.findRandomForGrammarPoints("N4", Set.of(3L), 15)).thenReturn(List.of());

        assertThat(practiceService.practice(" n4 ", List.of(3L, 3L))).isEmpty();
    }

    @Test
    void practice_shouldSendConnectionAndExplanationOfEachGrammarPoint() {
        ExamQuestion question = ExamQuestion.builder().id(10L).questionType(JlptQuestionType.GRAMMAR_FORM)
                .correctOption("A").grammarPointIds(Set.of(3L)).build();
        when(questionRepository.findRandomForGrammarPoints("N4", Set.of(3L), 15)).thenReturn(List.of(question));
        when(questionRepository.findAllWithLinksByIdIn(List.of(10L))).thenReturn(List.of(question));
        when(grammarPointRepository.findAllById(Set.of(3L))).thenReturn(List.of(GrammarPoint.builder().id(3L)
                .pattern("〜ために").meaningVi("Để ~").connection("Vる／N の + ために")
                .explanationVi("Mục đích, đi với động từ ý chí.\nVí dụ: 家を買うために、貯金しています。").build()));

        List<PracticeQuestionResponse> practice = practiceService.practice("N4", List.of(3L));

        assertThat(practice).singleElement().satisfies(item -> assertThat(item.grammarPoints())
                .extracting(QuestionReviewItem.Grammar::connection, QuestionReviewItem.Grammar::explanationVi)
                .containsExactly(tuple("Vる／N の + ために",
                        "Mục đích, đi với động từ ý chí.\nVí dụ: 家を買うために、貯金しています。")));
    }

    @Test
    void weakPoints_shouldSendConnectionAndExplanation() {
        GrammarPointRepository.LearnerMistakes row = mock(GrammarPointRepository.LearnerMistakes.class);
        when(row.getId()).thenReturn(3L);
        when(row.getPattern()).thenReturn("〜ために");
        when(row.getMeaningVi()).thenReturn("Để ~");
        when(row.getConnection()).thenReturn("Vる／N の + ために");
        when(row.getExplanationVi()).thenReturn("Mục đích, đi với động từ ý chí.");
        when(row.getWrong()).thenReturn(2L);
        when(row.getAnswered()).thenReturn(5L);
        when(grammarPointRepository.findMistakesOfLearner(eq(7L), eq("N4"), any(LocalDateTime.class),
                eq(GrammarPracticeService.MAX_WEAK_POINTS))).thenReturn(List.of(row));

        assertThat(practiceService.weakPoints(7L, "N4")).containsExactly(new WeakGrammarResponse(3L, "〜ために",
                "Để ~", "Vる／N の + ために", "Mục đích, đi với động từ ý chí.", 2, 5));
    }
}
