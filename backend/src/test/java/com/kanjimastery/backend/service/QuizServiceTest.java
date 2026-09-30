package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final Long TAG_ID = 5L;

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private SentenceGenerationService sentenceGenerationService;
    @Mock
    private RateLimiterService rateLimiter;

    private QuizService quizService;

    // 乾く đã thất bại 2 lần gần đây; 渇く chưa có câu; 固い đã có câu ví dụ.
    private final Kanji failedWord = word(1L, "乾く", "かわく", null);
    private final Kanji missingWord = word(2L, "渇く", "かわく", null);
    private final Kanji readyWord = word(3L, "固い", "かたい", "この肉は固い。");

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getQuiz().setLimit(30);
        properties.getQuiz().setWindowSeconds(60);
        quizService = new QuizService(kanjiRepository, sentenceGenerationService, rateLimiter, properties);
    }

    @AfterEach
    void tearDown() {
        quizService.shutdownExecutor();
    }

    @Test
    void generate_shouldRejectRequest_whenUserExceedsQuizLimit() {
        when(rateLimiter.tryAcquire("ratelimit:quiz:taro", 30, Duration.ofSeconds(60))).thenReturn(false);

        assertThatThrownBy(() -> quizService.generate("taro", TAG_ID, null, 10))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(kanjiRepository, sentenceGenerationService);
    }

    @Test
    void generate_shouldNotSendWordsThatFailedRecently() {
        givenLessonWithGeminiAnswer(Optional.of(Map.of()));

        quizService.generate("taro", TAG_ID, null, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Kanji>> batch = ArgumentCaptor.forClass(List.class);
        verify(sentenceGenerationService).generateSentences(batch.capture());
        assertThat(batch.getValue()).extracting(Kanji::getId).containsExactly(2L);
    }

    @Test
    void generate_shouldRecordFailure_whenGeminiAnsweredWithoutAUsableSentence() {
        givenLessonWithGeminiAnswer(Optional.of(Map.of()));

        quizService.generate("taro", TAG_ID, null, 10);

        verify(rateLimiter).increment("sentence:failures:2", Duration.ofHours(24));
    }

    @Test
    void generate_shouldNotRecordFailure_whenGeminiDidNotAnswer() {
        givenLessonWithGeminiAnswer(Optional.empty());

        quizService.generate("taro", TAG_ID, null, 10);

        verify(rateLimiter, never()).increment(startsWith("sentence:failures:"), any());
    }

    @Test
    void generate_shouldSaveNewSentence_andNotCountItAsFailure() {
        givenLessonWithGeminiAnswer(Optional.of(Map.of(2L, "のどが渇く。")));

        quizService.generate("taro", TAG_ID, null, 10);

        verify(kanjiRepository).saveExampleSentenceIfAbsent(2L, "のどが渇く。");
        verify(rateLimiter, never()).increment(startsWith("sentence:failures:"), any());
    }

    private void givenLessonWithGeminiAnswer(Optional<Map<Long, String>> answer) {
        when(rateLimiter.tryAcquire("ratelimit:quiz:taro", 30, Duration.ofSeconds(60))).thenReturn(true);
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(failedWord, missingWord, readyWord));
        when(sentenceGenerationService.isEnabled()).thenReturn(true);
        when(rateLimiter.counts(anyList())).thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream()
                .map(key -> key.equals("sentence:failures:1") ? 2L : 0L)
                .toList());
        when(sentenceGenerationService.generateSentences(anyList())).thenReturn(answer);
    }

    private static Kanji word(Long id, String character, String reading, String sentence) {
        return Kanji.builder()
                .id(id)
                .character(character)
                .reading(reading)
                .hanViet("")
                .meaning("nghĩa của " + character)
                .jlptLevel("N3")
                .strokeCount(10)
                .exampleSentence(sentence)
                .build();
    }
}
