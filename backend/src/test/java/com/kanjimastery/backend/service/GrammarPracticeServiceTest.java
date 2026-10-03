package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarPracticeServiceTest {

    @Mock
    private GrammarPointRepository grammarPointRepository;
    @Mock
    private ExamQuestionRepository questionRepository;

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
}
